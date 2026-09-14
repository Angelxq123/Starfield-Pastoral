package com.stardew.craft.monster;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** Regression for client packet 3 reaching ServerLevel-only species death hooks. */
class ClientDeathBoundaryTest {
    @Test void clientDeathRunsVanillaAndReturnsBeforeServerSettlement() throws Exception {
        var type = new ClassNode();
        try (var bytes = Objects.requireNonNull(getClass().getResourceAsStream(
                "/com/stardew/craft/monster/StardewMonsterEntity.class"))) {
            new ClassReader(bytes).accept(type, 0);
        }
        var die = type.methods.stream().filter(m -> m.name.equals("die")).findFirst().orElseThrow();
        FieldInsnNode side = null;
        for (var instruction : die.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("isClientSide")) {
                side = field;
                break;
            }
            if (instruction instanceof MethodInsnNode call) {
                assertEquals("level", call.name, "No settlement or species call may precede the side guard");
            }
        }
        assertNotNull(side, "Client deaths must be separated before invoking server-only hooks");
        var branch = (JumpInsnNode) nextCode(side);
        assertEquals(Opcodes.IFEQ, branch.getOpcode());
        int vanillaCalls = 0;
        boolean returned = false;
        for (var instruction = branch.getNext(); instruction != branch.label; instruction = instruction.getNext()) {
            assertNotNull(instruction);
            if (instruction instanceof MethodInsnNode call) {
                assertEquals(Opcodes.INVOKESPECIAL, call.getOpcode());
                assertEquals(type.superName, call.owner);
                assertEquals("die", call.name, "Client branch must only run vanilla death handling");
                vanillaCalls++;
            }
            if (instruction.getOpcode() == Opcodes.RETURN) returned = true;
        }
        assertEquals(1, vanillaCalls);
        assertTrue(returned, "Client must return before native settlement and particle broadcasts");
    }

    private AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction.getOpcode() < 0);
        return instruction;
    }
}
