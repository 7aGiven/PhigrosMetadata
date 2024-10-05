import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.Symbol;
import com.github.unidbg.arm.backend.DynarmicFactory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("help: java -jar PhigrosMetadata.jar <apkPath>");
            return;
        }
        try (ZipFile zipFile = new ZipFile(args[0])) {
            readEntry(zipFile, "lib/armeabi-v7a/libUnityPlugin.so", "libUnityPlugin.so");
            readEntry(zipFile, "assets/bin/Data/Managed/Metadata/game.dat", "game.dat");
        }
        AndroidEmulator emulator = AndroidEmulatorBuilder
                .for32Bit()
                .setProcessName("com.PigeonGames.Phigros")
                .setRootDir(new File("."))
                .addBackendFactory(new DynarmicFactory(false))
                .build();
        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));

        Module module = emulator.loadLibrary(new File("libUnityPlugin.so"));
        Symbol symbol = module.findSymbolByName("_Z26il2cpp_get_global_metadataPKc");

        String path = "/game.dat";
        MemoryBlock block = memory.malloc(path.length()+1, false);
        block.getPointer().setString(0, path);


        int number = symbol.call(emulator, block.getPointer()).intValue();
        UnidbgPointer metadata = UnidbgPointer.pointer(emulator, number);
        int offset = metadata.getInt(8) + metadata.getInt(12);
        int size = metadata.getInt(offset - 8) + metadata.getInt(offset - 4);
        int stringSize = metadata.getInt(28);
        UnidbgPointer string = UnidbgPointer.pointer(emulator, number+metadata.getInt(24));

        offset = 0;
        int xor;
        while (offset < stringSize) {
            xor = offset % 0xFF;
            do {
                xor ^= Byte.toUnsignedInt(string.getByte(offset));
                string.setByte(offset, (byte) xor);
                offset++;
            } while (xor != 0);
        }
        byte[] bytes = metadata.getByteArray(0, size);
        try (FileOutputStream outputStream = new FileOutputStream("metadata.dat")) {
            outputStream.write(bytes);
        }
    }
    static void readEntry(ZipFile zipFile, String zipPath, String dstPath) throws IOException {
        byte[] bytes = new byte[4*1024];
        ZipEntry entry = zipFile.getEntry(zipPath);
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            try (FileOutputStream outputStream = new FileOutputStream(dstPath)) {
                while (true) {
                    int len = inputStream.read(bytes);
                    if (len == -1)
                        break;
                    outputStream.write(bytes, 0, len);
                }
            }
        }
    }
}
