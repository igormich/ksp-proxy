package com.igormich.proxy

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStream
import kotlin.reflect.KClass

class ProxyProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    inner class Visitor(
        private val file: OutputStream,
        val resolver: Resolver,
        val interfaces2proxies: MutableMap<String, String>
    ) : KSVisitorVoid() {

        private fun generateProxy(proxyCall: String, clazz: KSClassDeclaration, proxyClassInit: String = "") {
            val clazzName = "ProxyFor${clazz.simpleName.asString()}"
            val qualifiedName = clazz.qualifiedName!!.asString()

            interfaces2proxies[qualifiedName] = clazzName
            file += "@Suppress(\"UNCHECKED_CAST\")\n"
            file += "class $clazzName: $qualifiedName {\n"
            if (proxyClassInit.isNotEmpty()) {
                file += "\tcompanion object {\n"
                file += "\t\t$proxyClassInit\n"
                file += "\t}\n"
            }
            for (property in clazz.getAllProperties()) {
                val propertyName = property.simpleName.asString()
                val (clazzType, fullType) = decode(property.type)
                val capitalName = propertyName.replaceFirstChar { it.uppercaseChar() }
                if (property.isMutable) {
                    file += "\toverride var ${propertyName}:$fullType\n"
                    file += "\t\tset(value) = $proxyCall(Method(\"set$capitalName\",arrayOf($clazzType::class),Unit::class),arrayOf(value))  as Unit\n"
                } else {
                    file += "\toverride val ${propertyName}:$fullType\n"
                }
                file += "\t\tget() = $proxyCall(Method(\"get$capitalName\",emptyArray(),$clazzType::class),emptyArray()) as $fullType\n"
            }
            for (func in clazz.getAllFunctions()) {
                val functionName = func.simpleName.asString()
                val (clazzType, fullType) = decode(func.returnType!!)
                val parameters = func.parameters.joinToString {
                    val (_, fullType) = decode(it.type)
                    "${it.name!!.asString()} : $fullType"
                }

                val parametersClasses = func.parameters.joinToString {
                    val (clazzType, _) = decode(it.type)
                    "$clazzType::class"
                }
                file += "\toverride fun ${functionName}($parameters):$fullType {\n"
                file += "\t\treturn $proxyCall(Method(\"$functionName\",arrayOf($parametersClasses),$clazzType::class),emptyArray()) as $fullType\n"
                file += "}\n"
            }
            file += "}\n"
        }

        @OptIn(KspExperimental::class)
        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            val proxyCall = function.qualifiedName!!.asString()
            val errorText = "$proxyCall must have following signature: " +
                    "fun $proxyCall(method: com.igormich.proxy.Method, args: Array<Any>): Any"
            if (function.parameters.size != 2) {
                logger.error(errorText)
            }
            val (method, args) = function.parameters
            val methodType = method.type.resolve().declaration.qualifiedName!!.asString()
            val argsType = args.type.resolve().declaration.qualifiedName!!.asString()
            val returnType = function.returnType!!.resolve().declaration.qualifiedName!!.asString()
            val argsGeneric = resolver.getJavaWildcard(args.type).toString()
            if (methodType != "com.igormich.proxy.Method" || argsType != "kotlin.Array"
                || returnType != "kotlin.Any" || argsGeneric != "Array<Any>") {
                logger.error(errorText)
            }

            //TODO: add args check
            val annotation: KSAnnotation = function.annotations.first {
                it.shortName.asString() == "ProxyFor"
            }
            val clazz = (annotation.arguments.first().value as KSType).declaration as KSClassDeclaration
            if (clazz.classKind != ClassKind.INTERFACE) {
                logger.error("Proxy can be generated only for interfaces but ${clazz.simpleName} is not interfaces")
            }
            generateProxy(proxyCall, clazz)
        }

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {

            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                logger.error("Proxy can be generated only for interfaces but ${classDeclaration.simpleName} is not interfaces")
            }
            val annotation: KSAnnotation = classDeclaration.annotations.first {
                it.shortName.asString() == "WithProxy"
            }

            val proxyClazz = (annotation.arguments.first().value as KSType).declaration as KSClassDeclaration

            if (proxyClazz.classKind == ClassKind.CLASS) {
                if (proxyClazz.isAbstract()) {
                    logger.error("${proxyClazz.simpleName.asString()} must not be abstract")
                }
                val canCallEmptyConstructor = proxyClazz.declarations
                    .filterIsInstance<KSFunctionDeclaration>()
                    .filter { it.isConstructor() }
                    .any { it.parameters.all { p -> p.hasDefault } }
                if (!canCallEmptyConstructor) {
                    logger.error("${proxyClazz.simpleName.asString()} must have constructor without args")
                    return
                }
                val proxyClazzName = proxyClazz.qualifiedName!!.asString()
                val simpleName = classDeclaration.simpleName.asString()
                generateProxy(
                    "ProxyFor$simpleName.proxy.handle",
                    classDeclaration,
                    proxyClassInit = "val proxy = $proxyClazzName()"
                )

            } else if (proxyClazz.classKind == ClassKind.OBJECT) {
                val proxyClazzName = proxyClazz.qualifiedName!!.asString()
                generateProxy("$proxyClazzName.handle", classDeclaration)
            } else {
                logger.error("${proxyClazz.simpleName.asString()} must be class or object")
                return
            }
        }

        private fun decode(kstypeReference: KSTypeReference): Pair<String, String> {
            val type = kstypeReference.resolve()
            val clazzType = type.declaration.qualifiedName!!.asString()
            val generic = if (type.arguments.isNotEmpty())
                type.arguments.map { it.type }.joinToString(prefix = "<", postfix = ">")
            else
                ""
            val fullType = clazzType + generic + if (type.nullability == Nullability.NULLABLE) {
                "?"
            } else {
                ""
            }
            return clazzType to fullType
        }
    }

    operator fun OutputStream.plusAssign(str: String) {
        this.write(str.toByteArray())
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val proxyFor = resolver
            .getSymbolsWithAnnotation("com.igormich.proxy.ProxyFor")
            .filterIsInstance<KSFunctionDeclaration>()
        val withProxy = resolver
            .getSymbolsWithAnnotation("com.igormich.proxy.WithProxy")
            .filterIsInstance<KSClassDeclaration>()
        if (!proxyFor.iterator().hasNext()) return emptyList()
        try {
            val interfaces2proxies = mutableMapOf<String, String>()
            val file: OutputStream = codeGenerator.createNewFile(
                dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray()),
                packageName = "com.igormich.proxy",
                fileName = "GeneratedProxies"
            )
            file += "package com.igormich.proxy\n"
            file += "/*PLEASE DON`T EDIT THIS FILE, IT WILL BE REBUILD IN COMPILE TIME*/"
            file += "import kotlin.reflect.KClass\n"
            proxyFor.forEach { it.accept(Visitor(file, resolver, interfaces2proxies), Unit) }
            withProxy.forEach { it.accept(Visitor(file, resolver, interfaces2proxies), Unit) }

            file += "@Suppress(\"UNCHECKED_CAST\")\n"
            file += "object ProxyProvider {\n"
            file +="\tval map = mapOf<KClass<out Any>, () -> Any>(\n"
            for((i,c) in interfaces2proxies) {
                file +="\t\t$i::class to {$c()},\n"
            }
            file += "\t)\n"

            file += "\tfun <T: Any> createProxyFor(clazz:KClass<out T>):T {\n"
            file += "\t\tval result = map[clazz]?.invoke()?:throw IllegalStateException(\"No Proxy found for \${clazz.qualifiedName}\")\n"
            file += "\t\treturn result as T\n"
            file += "\t}\n"
            file += "\tinline fun <reified T: Any> createProxyFor():T {\n" +
                    "\t\treturn createProxyFor(T::class)\n"
            file += "\t}\n"
            file += "}\n"
            file.flush()
            file.close()
            logger.warn(proxyFor.filterNot { it.validate() }.toList().toString())
            return proxyFor.filterNot { it.validate() }.toList()
        } catch (e: Exception) {
            return emptyList()
        }

    }
}