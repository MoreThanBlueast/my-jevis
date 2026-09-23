from jevis.services.app_registry import infer_target_app


def test_infers_supported_apps_from_natural_language():
    assert infer_target_app("查询本周日程并整理摘要") == "com.android.calendar"
    assert infer_target_app("打开高德地图规划路线") == "com.autonavi.minimap"
    assert infer_target_app("在便签中记录购物清单") == "com.miui.notes"


def test_unknown_goal_requires_model_resolution():
    assert infer_target_app("帮我处理这件事") is None
