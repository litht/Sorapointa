package org.sorapointa.lua

import net.sandius.rembulan.runtime.LuaFunction
import java.io.BufferedReader
import java.io.Reader
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.script.*
import kotlin.reflect.KClass

object LuaScriptEngine : AbstractScriptEngine(), Invocable {

    private val luaExecutor = LuaExecutor

    fun putAll(bindings: Map<String, Any>) =
        bindings.forEach { (k, v) -> put(k, v) }

    override fun eval(script: String, context: ScriptContext): Any? {
        val luaContext: MutableMap<String, Any?> = HashMap()
        context.getBindings(ScriptContext.GLOBAL_SCOPE)?.let { luaContext.putAll(it) }
        context.getBindings(ScriptContext.ENGINE_SCOPE)?.let { luaContext.putAll(it) }

        return luaExecutor.runWithContext(script, luaContext).firstOrNull()
    }

    override fun eval(reader: Reader, context: ScriptContext): Any? {
        val script = reader.buffered().use(BufferedReader::readText)
        return eval(script, context)
    }

    override fun createBindings(): Bindings = SimpleBindings()

    override fun getFactory(): ScriptEngineFactory = LuaScriptEngineFactory

    fun invokeMethod(thiz: LuaScriptEngine, name: String, vararg args: Any?): Any? {
        val results = thiz.luaExecutor.call(name, args)
        return if (results?.size == 1) results.first() else results
    }

    override fun invokeMethod(thiz: Any?, name: String, vararg args: Any?): Any? {
        if (thiz !is LuaScriptEngine) {
            throw ScriptException("the target object is not a class or subclass of LuaScriptEngine")
        }
        return invokeMethod(thiz, name, args)
    }

    override fun invokeFunction(name: String, vararg args: Any?): Any? {
        if (luaExecutor.getFunction(name) == null) {
            val bindings = getBindings(ScriptContext.ENGINE_SCOPE)
            if (bindings?.get(name) is LuaFunction) {
                luaExecutor.putContext(bindings)
            } else throw LuaException("no such method called $name")
        }
        val results = luaExecutor.call(name, args = args)
        return if (results?.size == 1) results.first() else results
    }

    fun <T : Any> getInterface(clazz: KClass<T>): T? = getInterface(clazz.java)

    @Suppress("UNCHECKED_CAST")
    override fun <T> getInterface(clazz: Class<T>): T? =
        runCatching {
            clazz.declaredMethods.forEach { m ->
                if (luaExecutor.getFunction(m.name) != null) return@forEach
                val bindings = getBindings(ScriptContext.ENGINE_SCOPE)
                if (bindings?.get(m.name) is LuaFunction) {
                    luaExecutor.putContext(bindings)
                } else throw LuaException("no such method called ${m.name}")
            }
            Proxy.newProxyInstance(
                clazz.classLoader, arrayOf(clazz),
                LuaInvocationHandler(luaExecutor)
            ) as T
        }.getOrNull()

    override fun <T> getInterface(target: Any?, clazz: Class<T>): T? =
        if (target is LuaScriptEngine) {
            target.getInterface(clazz)
        } else null

    private class LuaInvocationHandler(private val executor: LuaExecutor) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any>): Any? {
            return executor.call(method.name, args = args)?.firstOrNull()
        }
    }
}