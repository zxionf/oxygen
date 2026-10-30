# Agent State

Active Change: 20261030-multi-module-split（sele line，active）
Phase: 架构调整为三模块——`shio-vlt`（密码库应用，原 app）、`shared`（JNI 包装 + 内核封装 + 通用 UI 占位）、`shio-todo`（待办应用占位）
Blocked: 无
Next: 待 line 合并后，真机验收「原密码库数据仍在 + todo 可启动」；之后回到 docs/todo.md 的功能清单
Note: 命令改为 `:shio-vlt:` / `:shared:` / `:shio-todo:`；release 产物 `shio-vlt/build/outputs/apk/release/shio-vlt-release.apk`；shared 的 release 靠 CMake `-s` 去符号表；`.design/DECISIONS.log` 停止维护，新决策走 sele line 描述
