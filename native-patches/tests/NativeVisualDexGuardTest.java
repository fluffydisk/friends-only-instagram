import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import java.util.List;

public final class NativeVisualDexGuardTest {
    static ImmutableMethod method(int destination) {
        return new ImmutableMethod("Lfixture/Avatar;", "onDraw", List.of(), "V", 1, java.util.Set.of(), java.util.Set.of(),
            new ImmutableMethodImplementation(1, List.of(
                new ImmutableInstruction21t(Opcode.IF_EQZ, 0, destination),
                new ImmutableInstruction31i(Opcode.CONST, 0, 100),
                new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of()));
    }
    public static void main(String[] args) {
        int checks = 0;
        for (int good : new int[]{0, 2, 5}) { NativeVisualDexGuard.validateTargets(method(good)); checks++; }
        for (int invalid : new int[]{-1, 1, 3, 4, 6, 99}) {
            boolean rejected = false;
            try { NativeVisualDexGuard.validateTargets(method(invalid)); }
            catch (IllegalStateException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Accepted operand/out-of-range target " + invalid);
            checks++;
        }
        System.out.println("NativeVisualDexGuardTest: " + checks + " assertions passed");
    }
}
