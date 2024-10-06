import com.github.unidbg.Emulator;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.file.SimpleFileIO;

import java.io.File;

public class IO implements IOResolver<AndroidFileIO> {
	@Override
	public FileResult<AndroidFileIO> resolve(Emulator<AndroidFileIO> emulator, String path, int mode) {
		if (path.equals("/game.dat")) {
			File file = new File("game.dat");
			return FileResult.success(new SimpleFileIO(mode, file, path));
		}
		return null;
	}
}

