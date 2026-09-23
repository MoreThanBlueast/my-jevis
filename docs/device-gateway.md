# Device Gateway contract

The executor is deliberately separate from the user-facing Android app. It runs in the AI profile and must prove that it owns a non-zero virtual display before receiving a task.

1. `POST /api/v1/devices/register` reports the capability probe.
2. `POST /api/v1/devices/{deviceId}/next-task` atomically claims one queued task.
3. The response pins `required_display_id` and `required_profile_user_id`.
4. Every observation/action/result is sent to `POST /api/v1/devices/{deviceId}/tasks/{taskId}/events`.
5. An event from display `0`, another profile, or an out-of-order sequence is rejected.

The first physical-device milestone is to implement this contract in the work-profile executor after the Xiaomi 15 Ultra capability probe succeeds. No API path permits a fallback to the physical display.

