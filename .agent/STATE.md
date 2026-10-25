# Agent State

Active Change: 20261023-password-vault（M1–M16 完成）
Phase: 外观偏好与自动锁定偏好都落 SharedPreferences，冷启动恢复已真机验证
Blocked: 无（真机 10AE4N38SA000LK 可用）
Next: M17 自动锁定生效（onStop/onStart + isChangingConfigurations；默认立即，1/5/15 分钟按时间戳）
Note: 真机当前偏好：LIGHT + TEAL、自动锁定 5 分钟；单测 3 组 18 例
