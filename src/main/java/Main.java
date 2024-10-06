import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.Symbol;
import com.github.unidbg.arm.backend.DynarmicFactory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.virtualmodule.android.AndroidModule;

public class Main {
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.out.println("help: java -jar PhigrosMetadata.jar <apkPath>");
			return;
		}
		try (ZipFile zipFile = new ZipFile(args[0])) {
			readEntry(zipFile, "lib/armeabi-v7a/libUnityPlugin.so", "libUnityPlugin.so");
			readEntry(zipFile, "assets/bin/Data/Managed/Metadata/game.dat", "game.dat");
		}
		AndroidEmulator emulator = AndroidEmulatorBuilder
				.for32Bit()
				.addBackendFactory(new DynarmicFactory(false))
				.build();
		Memory memory = emulator.getMemory();
		memory.setLibraryResolver(new AndroidResolver(23));

		emulator.getSyscallHandler().addIOResolver(new IO());
		
		VM vm = emulator.createDalvikVM();
		new AndroidModule(emulator, vm).register(memory);

        DalvikModule dm = vm.loadLibrary(new File("libUnityPlugin.so"), false);
        dm.callJNI_OnLoad(emulator);

        Module module = dm.getModule();
		// Module module = emulator.loadLibrary(new File("libUnityPlugin.so"));
		Symbol symbol = module.findSymbolByName("_Z26il2cpp_get_global_metadataPKc");

		String path = "/game.dat";
		MemoryBlock block = memory.malloc(path.length()+1, false);
		block.getPointer().setString(0, path);


		int number = symbol.call(emulator, block.getPointer()).intValue();
		UnidbgPointer pMetadata = UnidbgPointer.pointer(emulator, number);
		int offset = pMetadata.getInt(8);
		int size = pMetadata.getInt(offset - 8) + pMetadata.getInt(offset - 4);

		ByteBuffer metadata = pMetadata.getByteBuffer(0, size);
		int stringSize = metadata.getInt(28);
		int string = metadata.getInt(24);

		offset = 0;
		int xor;
		while (offset < stringSize) {
			xor = offset % 0xFF;
			do {
				xor ^= Byte.toUnsignedInt(metadata.get(string+offset));
				metadata.put(string+offset, (byte) xor);
				offset++;
			} while (xor != 0);
		}
		try (FileOutputStream outputStream = new FileOutputStream("metadata.dat")) {
			outputStream.write(metadata.array());
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
