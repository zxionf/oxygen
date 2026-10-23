# shio JNI 薄层

`include/shio.h` 是 hydrogen 内核 C ABI 的只读副本，来源：

- 仓库：`hydrogen`
- 提交：`50e073b3998c690811414aa9534393b4033cac35`
- 路径：`crates/shio-ffi/include/shio.h`
- sha256：`ba156f49fb3c144e98506b6b8faef76d6c67dccbe7b5d5ebfcaa405f0a119846`

升级 `libshio_ffi.so` 时，必须同时复制同一提交的头文件。

- Linux：`scripts/sync-shio.sh` 会同时同步二进制与 `include/shio.h`，并校验 ELF 架构与 16 KB page 对齐；`--skip-build` 可只复制 `target` 下已有产物。
- Windows：`scripts/sync-shio.ps1` 只同步二进制，头文件需要手动复制并更新上面的 sha256。

`CMakeLists.txt` 用 `IMPORTED` 目标链接 `src/main/jniLibs/<abi>/libshio_ffi.so`，
产出 `libshio_jni.so`。C++ 运行时使用静态 STL，不需要额外打包 `libc++_shared.so`。
