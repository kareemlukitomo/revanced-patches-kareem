package dev.kareemlukitomo.revanced.shared

import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

private data class MethodSignature(
    val definingClass: String,
    val name: String,
    val returnType: String,
    val parameterTypes: List<String>,
)

fun BytecodePatchContext.ClassDefs.replaceExactStrings(replacements: Map<String, String>): Int {
    val targetMethods = linkedMapOf<MethodSignature, Method>()
    replacements.keys.forEach { original ->
        methodsByString[original].orEmpty().forEach { method ->
            targetMethods.putIfAbsent(method.signature(), method)
        }
    }

    var replacementCount = 0
    targetMethods.values.forEach { method ->
        val targetClass = get(method.definingClass)
            ?: throw PatchException("Could not find target class ${method.definingClass}")
        val mutableClass = getOrReplaceMutable(targetClass)
        val mutableMethod = mutableClass.methods.firstOrNull { it.sameSignature(method) }
            ?: throw PatchException("Could not find target method ${method.definingClass}->${method.name}")
        val implementation = mutableMethod.implementation
            ?: throw PatchException("Target method ${method.definingClass}->${method.name} has no implementation")

        implementation.instructions.forEachIndexed { index, instruction ->
            val referenceInstruction = instruction as? ReferenceInstruction ?: return@forEachIndexed
            val stringReference = referenceInstruction.reference as? StringReference ?: return@forEachIndexed
            val replacement = replacements[stringReference.string] ?: return@forEachIndexed
            val register = (instruction as? OneRegisterInstruction)?.registerA
                ?: throw PatchException(
                    "String instruction in ${method.definingClass}->${method.name} does not expose its target register",
                )

            implementation.replaceInstruction(
                index,
                BuilderInstruction31c(
                    Opcode.CONST_STRING_JUMBO,
                    register,
                    ImmutableStringReference(replacement),
                ),
            )
            replacementCount++
        }
    }

    return replacementCount
}

private fun Method.signature() = MethodSignature(
    definingClass = definingClass,
    name = name,
    returnType = returnType,
    parameterTypes = parameterTypes.map(CharSequence::toString),
)

private fun Method.sameSignature(other: Method) = signature() == other.signature()
