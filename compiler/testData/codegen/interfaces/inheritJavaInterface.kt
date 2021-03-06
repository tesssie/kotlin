// DONT_TARGET_EXACT_BACKEND: JS JS_IR JS_IR_ES6 WASM NATIVE
// MODULE: lib
// FILE: A.java

interface A {
    void foo();
}

// MODULE: main(lib)
// FILE: 1.kt

internal interface B : A {
    fun bar() = 1
}

internal interface C : B

internal class D : C {
    override fun foo() {}
}

fun box(): String {
    val d = D()
    d.foo()
    d.bar()
    return "OK"
}
