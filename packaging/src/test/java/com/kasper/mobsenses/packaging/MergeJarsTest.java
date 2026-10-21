package com.kasper.mobsenses.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class MergeJarsTest {

    @TempDir
    Path dir;

    private static byte[] emptyClass(String name, int version) {
        ClassWriter w = new ClassWriter(0);
        w.visit(version, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        w.visitEnd();
        return w.toByteArray();
    }

    private Path folder(String name, Map<String, byte[]> files) throws IOException {
        Path root = dir.resolve(name);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path p = root.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        return root;
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private Path band(String name, boolean refmap, int version) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("com/kasper/mobsenses/Common.class", emptyClass("com/kasper/mobsenses/Common", Opcodes.V17));
        files.put("com/kasper/mobsenses/MobSensesFabric.class", emptyClass("com/kasper/mobsenses/MobSensesFabric", version));
        files.put("com/kasper/mobsenses/mixin/LevelMixin.class", emptyClass("com/kasper/mobsenses/mixin/LevelMixin", version));
        files.put("fabric.mod.json", text("{}"));
        files.put("mobsenses.mixins.json", text("{\n  \"required\": true,\n  \"package\": \"com.kasper.mobsenses.mixin\",\n"
                + "  \"compatibilityLevel\": \"JAVA_17\",\n  \"mixins\": [\"LevelMixin\"],\n"
                + (refmap ? "  \"refmap\": \"mobsenses-refmap.json\",\n" : "")
                + "  \"injectors\": {\"defaultRequire\": 1}\n}\n"));
        if (refmap) {
            files.put("mobsenses-refmap.json", text("{\"mappings\": {\"com/kasper/mobsenses/mixin/LevelMixin\": {\"gameEvent\": \"Lx;m(I)V\"}}}"));
        }
        return folder(name, files);
    }

    @Test
    void everyBandGetsItsOwnMixinConfigAndRefmap() throws IOException {
        Path common = folder("common", Map.of("com/kasper/mobsenses/Common.class", emptyClass("com/kasper/mobsenses/Common", Opcodes.V1_8)));
        Path base = folder("base", Map.of("mobsenses-bands.properties", text("bands=mc1_21,mc26\n")));
        Map<String, Path> bands = new LinkedHashMap<>();
        bands.put("mc1_21", band("b21", true, Opcodes.V21));
        bands.put("mc26", band("b26", false, Opcodes.V21 + 4));
        Path out = dir.resolve("out.jar");

        MergeJars merge = new MergeJars();
        setMixins(merge);
        merge.run(out, java.util.List.of(common), java.util.List.of(base), bands, new HashMap<>(),
                Integer.MAX_VALUE, Integer.MAX_VALUE, "mobsenses-bands.properties");

        try (ZipFile zip = new ZipFile(out.toFile())) {
            assertNotNull(zip.getEntry("com/kasper/mobsenses/b/mc1_21/mixin/LevelMixin.class"));
            assertNotNull(zip.getEntry("com/kasper/mobsenses/b/mc26/mixin/LevelMixin.class"));
            assertNull(zip.getEntry("mobsenses.mixins.json"));
            assertNull(zip.getEntry("mobsenses-refmap.json"));
            String c21 = read(zip, "mobsenses-mc1_21.mixins.json");
            assertTrue(c21.contains("\"package\": \"com.kasper.mobsenses.b.mc1_21.mixin\""), c21);
            assertTrue(c21.contains("\"mixins\": []"), c21);
            assertTrue(c21.contains("\"plugin\": \"com.kasper.mobsenses.BandMixins\""), c21);
            assertTrue(c21.contains("\"refmap\": \"mobsenses-mc1_21-refmap.json\""), c21);
            String refmap = read(zip, "mobsenses-mc1_21-refmap.json");
            assertTrue(refmap.contains("com/kasper/mobsenses/b/mc1_21/mixin/LevelMixin"), refmap);
            String c26 = read(zip, "mobsenses-mc26.mixins.json");
            assertFalse(c26.contains("refmap"), c26);
            String table = read(zip, "mobsenses-bands.properties");
            assertTrue(table.contains("mc1_21.mixins=LevelMixin"), table);
            assertTrue(table.contains("mc26.mixins=LevelMixin"), table);
            assertFalse(table.contains(".mod="), table);
        }
    }

    @Test
    void aMissingRefmapIsAnError() throws IOException {
        Path common = folder("common", Map.of("com/kasper/mobsenses/Common.class", emptyClass("com/kasper/mobsenses/Common", Opcodes.V1_8)));
        Path base = folder("base", Map.of("mobsenses-bands.properties", text("bands=mc1_21\n")));
        Path broken = band("b21", true, Opcodes.V21);
        Files.delete(broken.resolve("mobsenses-refmap.json"));
        MergeJars merge = new MergeJars();
        setMixins(merge);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> merge.run(dir.resolve("out.jar"),
                java.util.List.of(common), java.util.List.of(base), Map.of("mc1_21", broken), new HashMap<>(),
                Integer.MAX_VALUE, Integer.MAX_VALUE, "mobsenses-bands.properties"));
        assertTrue(e.getMessage().contains("names the refmap"), e.getMessage());
    }

    private static void setMixins(MergeJars merge) {
        merge.mixinConfig = "mobsenses.mixins.json";
        merge.mixinPlugin = "com.kasper.mobsenses.BandMixins";
    }

    private static String read(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        assertNotNull(entry, name);
        try (var in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
