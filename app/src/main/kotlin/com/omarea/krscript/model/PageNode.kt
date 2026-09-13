package com.omarea.krscript.model

class PageNode(currentConfigXml: String) : ClickableNode(currentConfigXml) {
    var pageConfigPath: String = ""
    var pageConfigSh: String = ""
    var onlineHtmlPage: String = ""
    // 点击后要跳转的网页链接
    var link: String = ""
    // 点击后要打开的活动
    var activity: String = ""

    // 读取页面配置前
    var beforeRead = ""
    // 读取页面配置后
    var afterRead = ""

    // Danh sách menu 3 chấm + fab của trang - gán trực tiếp từ PageConfigReader/PageConfigSh
    // ngay khi đọc xong toml của CHÍNH trang này (xem ActionPage.loadPageConfig()). Không còn
    // cơ chế option-sh (script sinh menu động, khai báo ở trang cha) nữa - đã bỏ hẳn; muốn menu
    // động thì dùng "box"/"check" (điều kiện hiện/tích) hoặc "support"/"visible" ngay trên từng
    // mục trong [[menu.items]]/[[fab.items]].
    var pageMenuOptions: ArrayList<PageMenuOption>? = null
    // [[group.action]] menu = true: các action bị loại khỏi danh sách nội dung, hiện như icon
    // riêng trên toolbar thay vào đó. show = true: các action (bất kể menu true/false) cần tự
    // mở dialog ngay khi vào trang. Cả 2 gán trực tiếp từ PageConfigReader/PageConfigSh cùng
    // lúc với pageMenuOptions (xem ActionPage.loadPageConfig()).
    var headerActions: ArrayList<ActionNode>? = null
    var autoShowActions: ArrayList<ActionNode>? = null
    // Icon khai báo TRỰC TIẾP ở cấp container [[menu]]/[[fab]] (field "icon"/"icon-path", KHÔNG
    // phải trong "items") - gán cùng lúc với pageMenuOptions (xem ActionPage.loadPageConfig()).
    // menuIconNode: icon thay cho nút "⋮" mặc định trên toolbar (LUÔN bị ép tint theo màu icon
    // toolbar, giống mọi icon custom khác trong menu - xem ActionPage.buildOverflowMenuButton()).
    // fabIconNode: icon mặc định cho nút FAB khi item không tự set icon riêng (hoặc fab có nhiều
    // item) - xem ActionPage.resolveFabIcon().
    var menuIconNode: ClickableNode? = null
    var fabIconNode: ClickableNode? = null
    // ĐÃ BỎ pageHandlerSh (handler-sh) khỏi page - page giờ chỉ dùng để MỞ TRANG cho nhanh
    // (link/activity/config/config-sh), không còn kiêm nhiệm làm handler mặc định cho menu/fab
    // nữa. Handler mặc định cho từng nhóm giờ khai báo NGAY TRONG [[menu]]/[[fab]] của trang
    // (field "handler"/"handler-sh") - xem PageConfigReader.menuGroupOptionsToml(), đã được gộp
    // sẵn vào PageMenuOption.script lúc parse nên lúc click chỉ cần đọc option.script.

    // Giống text.rows / action.rows: cho phép page hiển thị thêm các dòng rich-text bên dưới
    val rows = ArrayList<TextNode.TextRow>()

    // 页面加载失败
    var loadSuccess = ""
    // 页面加载成功
    var loadFail = ""

    // process = true: trang hiện từng mục 1 ngay khi build xong (kèm thanh tiến trình dưới
    // toolbar) thay vì đợi build xong toàn bộ mới hiện - dùng cho trang có nhiều mục/nhiều
    // lệnh shell nên load lâu (xem PageConfigReader.tomlChildren, ActionPage.loadPageConfig).
    var process: Boolean = false
    // Số khung skeleton hiện sẵn lúc đầu khi process = true - đọc từ toml field
    // "placeholder-count" (xem PageConfigReader.pageNodeToml()); không khai báo thì giữ mặc
    // định = 1 (xem ActionListFragment.PROGRESSIVE_PLACEHOLDER_COUNT_DEFAULT).
    var placeholderCount: Int = 1
}
