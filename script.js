const cm = new CModule(`
unsigned int buildString(unsigned char* string, unsigned int offset) {
	unsigned char xor = offset % 0xFF;
	do {
		xor ^= string[offset];
		string[offset] = xor;
		offset++;
	} while (xor);
	return offset;
}
`)
const buildString = new NativeFunction(cm.buildString, "uint", ["pointer", "uint"])
function unityPlugin() {
	const libUnityPlugin = Process.getModuleByName("libUnityPlugin.so")
	Interceptor.attach(libUnityPlugin.getExportByName("_Z26il2cpp_get_global_metadataPKc"),{
		onEnter(args) {
			const path = args[0].readCString()
			const reg = new RegExp("/data/(.*)/files")
			this.packageName = path.match(reg)[1]
			console.log("il2cpp_get_global_metadata", path, args[1])
		},
		onLeave(retval) {
			const p = retval.add(retval.add(8).readU32() - 8)
			const size = p.readU32() + p.add(4).readU32()
			const meta = Memory.dup(retval, size)
			const stringOffset = meta.add(4*6).readU32()
			const stringSize = meta.add(4*7).readU32()
			const string = meta.add(stringOffset)
			console.log("fix start")
			let offset = 0
			while (offset < stringSize) {
				offset = buildString(string, offset)
			}
			console.log("fix end")
			console.log("dump start")
			const bytes = meta.readByteArray(size)
			const path = `/data/data/${this.packageName}/global-metadata.dat`
			File.writeAllBytes(path, bytes)
			console.log("dump success", path)
		}
	})
}

Interceptor.attach(Module.getExportByName("libdl.so","dlopen"),{
	onEnter(args) {
		this.name = args[0].readCString()
	},
	onLeave(retval) {
		if (this.name == "libUnityPlugin.so") {
			unityPlugin()
		}
	}
})
