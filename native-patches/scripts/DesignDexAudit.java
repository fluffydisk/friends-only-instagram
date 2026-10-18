/* SPDX-License-Identifier: GPL-3.0-only */
import com.android.tools.smali.dexlib2.Opcodes;
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
    static void update(MessageDigest digest, Object value) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
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
                            for (var instruction : body.getInstructions()) {
                                update(digest, instruction.getOpcode());
                                if (instruction instanceof FiveRegisterInstruction r) {
                                    update(digest, r.getRegisterCount());
                                    int[] registers = {r.getRegisterC(), r.getRegisterD(), r.getRegisterE(), r.getRegisterF(), r.getRegisterG()};
                                    for (int i = 0; i < r.getRegisterCount(); i++) update(digest, registers[i]);
                                } else if (instruction instanceof RegisterRangeInstruction r) {
                                    update(digest, r.getStartRegister()); update(digest, r.getRegisterCount());
                                } else {
                                    if (instruction instanceof OneRegisterInstruction r) update(digest, r.getRegisterA());
                                    if (instruction instanceof TwoRegisterInstruction r) update(digest, r.getRegisterB());
                                    if (instruction instanceof ThreeRegisterInstruction r) update(digest, r.getRegisterC());
                                }
                                if (instruction instanceof WideLiteralInstruction r) update(digest, r.getWideLiteral());
                                if (instruction instanceof OffsetInstruction r) update(digest, r.getCodeOffset());
                                if (instruction instanceof ReferenceInstruction r) update(digest, r.getReference());
                                if (instruction instanceof DualReferenceInstruction r) update(digest, r.getReference2());
                                if (instruction instanceof SwitchPayload p) for (var element : p.getSwitchElements()) {
                                    update(digest, element.getKey()); update(digest, element.getOffset());
                                }
                                if (instruction instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload p) {
                                    update(digest, p.getElementWidth()); update(digest, p.getArrayElements());
                                }
                            }
                            for (var block : body.getTryBlocks()) {
                                update(digest, block.getStartCodeAddress()); update(digest, block.getCodeUnitCount());
                                for (var handler : block.getExceptionHandlers()) {
                                    update(digest, handler.getExceptionType()); update(digest, handler.getHandlerCodeAddress());
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
        if (args.length != 2) throw new IllegalArgumentException("Supply previous and design APK paths");
        var before = methods(Path.of(args[0]));
        var after = methods(Path.of(args[1]));
        if (!before.keySet().equals(after.keySet())) throw new IllegalStateException("Native method inventory changed");
        Set<String> changed = new TreeSet<>();
        for (String method : before.keySet()) if (!Arrays.equals(before.get(method), after.get(method))) changed.add(method);
        System.out.println("Native methods compared: " + before.size());
        System.out.println("Changed native methods: " + changed);
        if (!changed.equals(EXPECTED)) throw new IllegalStateException("Unexpected native method difference");
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
