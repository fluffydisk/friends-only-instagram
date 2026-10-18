/* SPDX-License-Identifier: GPL-3.0-only */
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Independent old/new native code and bundled-content comparison, not a phone/UI test. */
public final class DesignDexAudit {
    static final Set<String> EXPECTED = Set.of(
        "LX/01Mq;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;",
        "LX/0A3y;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;",
        "LX/07bY;-><init>(Landroid/view/View;)V");
    static final Set<String> REFERENCE = Set.of(
        "Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;I)V",
        "LX/06hD;-><init>(Landroid/content/Context;)V",
        "LX/07Ah;->A05(Landroid/content/Context;Landroid/view/ViewGroup;)Landroid/view/View;",
        "LX/00gJ;->A07(Landroid/view/LayoutInflater;Landroid/view/ViewGroup$LayoutParams;Landroid/view/ViewGroup;I)Landroid/view/View;",
        "Lcom/instagram/common/ui/widget/imageview/CircularImageView;->onDraw(Landroid/graphics/Canvas;)V",
        "Lcom/instagram/ui/widget/gradientspinner/GradientSpinner;->onDraw(Landroid/graphics/Canvas;)V");
    static void update(MessageDigest digest, Object value) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
    }
    static String target(Map<Integer, Integer> indexes, int address) {
        // Preserve an unresolvable original address exactly; never drop a branch.
        Integer index = indexes.get(address);
        return index == null ? "raw-address:" + address : "instruction:" + index;
    }
    static Map<String, byte[]> methods(Path apk) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().matches("classes(?:[0-9]+)?\\.dex")) continue;
                byte[] bytes; try (var input = zip.getInputStream(entry)) { bytes = input.readAllBytes(); }
                var dex = new DexBackedDexFile(Opcodes.forApi(35), ByteBuffer.wrap(bytes));
                for (var type : dex.getClasses()) {
                    if (type.getType().startsWith("Ldev/friendsonly/patch/runtime/")) continue;
                    for (var method : type.getMethods()) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        String signature = method.getDefiningClass() + "->" + method.getName()
                                + "(" + String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
                        update(digest, type.getSuperclass()); update(digest, type.getAccessFlags());
                        update(digest, type.getInterfaces()); update(digest, method.getAccessFlags());
                        var body = method.getImplementation();
                        if (body != null) {
                            update(digest, body.getRegisterCount());
                            // String-pool growth can widen CONST_STRING to JUMBO and move
                            // branch/try/payload byte addresses without changing instructions.
                            // Compare resolved references and control-flow instruction targets.
                            var code = new ArrayList<Instruction>();
                            body.getInstructions().forEach(code::add);
                            Map<Integer, Integer> indexes = new HashMap<>();
                            Map<Integer, Integer> switchOrigins = new HashMap<>();
                            Set<Integer> branchTargets = new HashSet<>();
                            Set<Integer> padding = new HashSet<>();
                            int address = 0;
                            for (var instruction : code) {
                                if (instruction instanceof OffsetInstruction r) branchTargets.add(address + r.getCodeOffset());
                                if (instruction.getOpcode() == Opcode.PACKED_SWITCH || instruction.getOpcode() == Opcode.SPARSE_SWITCH)
                                    switchOrigins.put(address + ((OffsetInstruction) instruction).getCodeOffset(), address);
                                address += instruction.getCodeUnits();
                            }
                            address = 0;
                            for (var instruction : code) {
                                if (instruction instanceof SwitchPayload payload && switchOrigins.containsKey(address))
                                    for (var element : payload.getSwitchElements()) branchTargets.add(switchOrigins.get(address) + element.getOffset());
                                address += instruction.getCodeUnits();
                            }
                            for (var block : body.getTryBlocks()) {
                                branchTargets.add(block.getStartCodeAddress());
                                branchTargets.add(block.getStartCodeAddress() + block.getCodeUnitCount());
                                for (var handler : block.getExceptionHandlers()) branchTargets.add(handler.getHandlerCodeAddress());
                            }
                            address = 0;
                            int logical = 0;
                            for (int i = 0; i < code.size(); i++) {
                                var instruction = code.get(i);
                                boolean alignment = instruction.getOpcode() == Opcode.NOP && (address & 1) == 1
                                    && i + 1 < code.size() && (code.get(i + 1) instanceof SwitchPayload ||
                                        code.get(i + 1) instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload)
                                    && !branchTargets.contains(address);
                                if (alignment) padding.add(i);
                                else indexes.put(address, logical++);
                                address += instruction.getCodeUnits();
                            }
                            indexes.put(address, logical);
                            address = 0;
                            for (int i = 0; i < code.size(); i++) {
                                var instruction = code.get(i);
                                if (padding.contains(i)) { address += instruction.getCodeUnits(); continue; }
                                update(digest, instruction.getOpcode() == Opcode.CONST_STRING_JUMBO ? Opcode.CONST_STRING : instruction.getOpcode());
                                if (instruction instanceof FiveRegisterInstruction r) {
                                    update(digest, r.getRegisterCount());
                                    int[] registers = {r.getRegisterC(), r.getRegisterD(), r.getRegisterE(), r.getRegisterF(), r.getRegisterG()};
                                    for (int register = 0; register < r.getRegisterCount(); register++) update(digest, registers[register]);
                                } else if (instruction instanceof RegisterRangeInstruction r) {
                                    update(digest, r.getStartRegister()); update(digest, r.getRegisterCount());
                                } else {
                                    if (instruction instanceof OneRegisterInstruction r) update(digest, r.getRegisterA());
                                    if (instruction instanceof TwoRegisterInstruction r) update(digest, r.getRegisterB());
                                    if (instruction instanceof ThreeRegisterInstruction r) update(digest, r.getRegisterC());
                                }
                                if (instruction instanceof WideLiteralInstruction r) update(digest, r.getWideLiteral());
                                if (instruction instanceof OffsetInstruction r) update(digest, target(indexes, address + r.getCodeOffset()));
                                if (instruction instanceof ReferenceInstruction r) update(digest, r.getReference());
                                if (instruction instanceof DualReferenceInstruction r) update(digest, r.getReference2());
                                if (instruction instanceof SwitchPayload p) for (var element : p.getSwitchElements()) {
                                    update(digest, element.getKey());
                                    update(digest, switchOrigins.containsKey(address) ? target(indexes, switchOrigins.get(address) + element.getOffset()) : "raw-offset:" + element.getOffset());
                                }
                                if (instruction instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload p) {
                                    update(digest, p.getElementWidth()); update(digest, p.getArrayElements());
                                }
                                address += instruction.getCodeUnits();
                            }
                            for (var block : body.getTryBlocks()) {
                                update(digest, target(indexes, block.getStartCodeAddress()));
                                update(digest, target(indexes, block.getStartCodeAddress() + block.getCodeUnitCount()));
                                for (var handler : block.getExceptionHandlers()) {
                                    update(digest, handler.getExceptionType()); update(digest, target(indexes, handler.getHandlerCodeAddress()));
                                }
                            }
                        }
                        if (result.put(signature, digest.digest()) != null) throw new IllegalStateException("Duplicate native method");
                    }
                }
            }
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) throw new IllegalArgumentException("Supply previous and design APK paths, optionally reference");
        var before = methods(Path.of(args[0]));
        var after = methods(Path.of(args[1]));
        if (!before.keySet().equals(after.keySet())) throw new IllegalStateException("Native method inventory changed");
        Set<String> changed = new TreeSet<>();
        for (String method : before.keySet()) if (!Arrays.equals(before.get(method), after.get(method))) changed.add(method);
        System.out.println("Native methods compared: " + before.size());
        System.out.println("Changed native methods: " + changed);
        Set<String> expected = args.length == 2 ? EXPECTED : switch (args[2]) {
            case "reference" -> REFERENCE;
            case "auth-recovery" -> Set.of("Lcom/instagram/common/ui/widget/imageview/CircularImageView;->onDraw(Landroid/graphics/Canvas;)V");
            default -> throw new IllegalArgumentException("Unknown audit scope");
        };
        if (!changed.equals(expected)) throw new IllegalStateException("Unexpected native method difference");
        int preservedAssets = 0, preservedLibraries = 0;
        try (ZipFile old = new ZipFile(args[0]); ZipFile current = new ZipFile(args[1])) {
            var entries = old.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName();
                if (entry.isDirectory() || !(name.startsWith("assets/") || name.startsWith("lib/"))) continue;
                var newer = current.getEntry(name);
                if (newer == null) throw new IllegalStateException("Removed original asset/library");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] oldHash, newHash;
                try (var input = old.getInputStream(entry)) { oldHash = digest.digest(input.readAllBytes()); }
                try (var input = current.getInputStream(newer)) { newHash = digest.digest(input.readAllBytes()); }
                if (!Arrays.equals(oldHash, newHash)) throw new IllegalStateException("Changed original asset/library: " + name);
                if (name.startsWith("assets/")) preservedAssets++; else preservedLibraries++;
            }
        }
        System.out.println("Original assets preserved: " + preservedAssets);
        System.out.println("Native libraries preserved: " + preservedLibraries);
        System.out.println("PASS: scoped native design diff; runtime rendering remains unverified");
    }
}
