/* SPDX-License-Identifier: GPL-3.0-only */
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.analysis.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.ZipFile;

/** Fail delivery on invalid visual/runtime DEX, including branches into operands. Not ART. */
public final class NativeVisualDexGuard {
    static final Set<String> NATIVE = Set.of(
        "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;", "LX/06hD;", "LX/07Ah;", "LX/00gJ;",
        "Lcom/instagram/common/ui/widget/imageview/CircularImageView;",
        "Lcom/instagram/ui/widget/gradientspinner/GradientSpinner;");
    static void validateTargets(Method method) {
        var body = method.getImplementation();
        if (body == null) return;
        Map<Integer, Instruction> instructions = new HashMap<>();
        Map<Integer, Integer> switchOrigins = new HashMap<>();
        int address = 0;
        for (var instruction : body.getInstructions()) {
            instructions.put(address, instruction);
            if (instruction.getOpcode() == Opcode.PACKED_SWITCH || instruction.getOpcode() == Opcode.SPARSE_SWITCH)
                switchOrigins.put(address + ((OffsetInstruction) instruction).getCodeOffset(), address);
            address += instruction.getCodeUnits();
        }
        int end = address;
        address = 0;
        for (var instruction : body.getInstructions()) {
            if (instruction instanceof OffsetInstruction branch) {
                int target = address + branch.getCodeOffset();
                if (!instructions.containsKey(target)) throw new IllegalStateException("Branch does not target an instruction boundary");
            }
            if (instruction instanceof SwitchPayload payload) {
                Integer origin = switchOrigins.get(address);
                if (origin == null) throw new IllegalStateException("Switch payload has no origin");
                for (var element : payload.getSwitchElements())
                    if (!instructions.containsKey(origin + element.getOffset())) throw new IllegalStateException("Invalid switch destination");
            }
            address += instruction.getCodeUnits();
        }
        for (var block : body.getTryBlocks()) {
            int limit = block.getStartCodeAddress() + block.getCodeUnitCount();
            if (!instructions.containsKey(block.getStartCodeAddress()) || (limit != end && !instructions.containsKey(limit)))
                throw new IllegalStateException("Invalid protected range");
            for (var handler : block.getExceptionHandlers())
                if (!instructions.containsKey(handler.getHandlerCodeAddress())) throw new IllegalStateException("Invalid exception handler");
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply final APK");
        Map<String, ClassDef> classes = new HashMap<>();
        try (var zip = new ZipFile(args[0])) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().matches("classes(?:[0-9]+)?\\.dex")) continue;
                var dex = new DexBackedDexFile(Opcodes.forApi(35), ByteBuffer.wrap(zip.getInputStream(entry).readAllBytes()));
                for (var type : dex.getClasses()) classes.put(type.getType(), type);
            }
        }
        if (!classes.keySet().containsAll(NATIVE) || !classes.containsKey("Ldev/friendsonly/patch/runtime/ReferenceChrome;"))
            throw new IllegalStateException("Required visual classes missing");
        ClassProvider platform = name -> {
            try {
                return new com.android.tools.smali.dexlib2.analysis.reflection.ReflectionClassDef(Class.forName(
                    name.substring(1, name.length() - 1).replace('/', '.'), false, NativeVisualDexGuard.class.getClassLoader()));
            } catch (ClassNotFoundException | LinkageError ignored) { return null; }
        };
        var path = new ClassPath(List.of((ClassProvider) classes::get, platform), true, 170);
        int analyzed = 0;
        for (var type : classes.values()) {
            if (!NATIVE.contains(type.getType()) && !type.getType().startsWith("Ldev/friendsonly/patch/runtime/")) continue;
            for (var method : type.getMethods()) {
                if (method.getImplementation() == null) continue;
                try {
                    validateTargets(method);
                    var analyzer = new MethodAnalyzer(path, method, null, true);
                    if (analyzer.getAnalysisException() != null) throw analyzer.getAnalysisException();
                } catch (RuntimeException failure) {
                    // Static method identity only, never runtime account data or exception messages.
                    throw new IllegalStateException("Invalid visual/runtime method: " + method.getDefiningClass() + "->" + method.getName(), failure);
                }
                analyzed++;
            }
        }
        System.out.println("{\"status\":\"passed\",\"guarded_native_classes\":6,\"analyzed_methods\":" + analyzed + ",\"android_runtime_test\":false}");
    }
}
