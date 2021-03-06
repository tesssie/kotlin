// DONT_TARGET_EXACT_BACKEND: JS JS_IR JS_IR_ES6 WASM NATIVE
// MODULE: lib
// FILE: J.java

public class J {
    protected static String protectedFun() {
        return "OK";
    }
}

// MODULE: main(lib)
// FILE: 1.kt

object A : J() {
    fun test(): String {
        return J.protectedFun()!!
    }
}

fun box(): String {
    return A.test()
}
