package com.omarea.krscript.model

import com.omarea.common.model.SelectItem
import java.io.Serializable

class ActionParamInfo : Serializable {
    // 参数名：必需保持唯一
    var name: String? = null

    var title: String? = null
    // Kịch bản shell sinh title động (nếu có, sẽ được gộp chạy cùng value-sh/options-sh
    // khi mở dialog action, rồi ghi đè lên title tĩnh)
    var titleSh: String? = null

    var label: String? = null
    // Kịch bản shell sinh label động
    var labelSh: String? = null

    // 描述
    var desc: String? = null
    // Kịch bản shell sinh desc động
    var descSh: String? = null

    // ========== TÍNH NĂNG MỚI: GHI CHÚ RIÊNG KHI CHECKBOX/SWITCH ĐANG BẬT ==========
    // Chỉ áp dụng cho type="bool"/"checkbox"/"switch". Khi có khai báo, phần ghi chú
    // (kr_param_desc) sẽ tự động đổi thành nội dung này ngay khi người dùng bật (check/on),
    // và tự đổi lại về `desc` gốc khi tắt (off) - không cần chờ chạy shell hay reload dialog.
    // Ví dụ: desc="Tắt để giữ tần số CPU mặc định" desc-on="Đã bật: CPU sẽ bị khoá ở mức tối đa"
    var descOn: String? = null
    // Kịch bản shell sinh desc-on động (nếu có, được gộp chạy CÙNG 1 LẦN với
    // value-sh/desc-sh/... khi mở dialog action - KHÔNG chạy lại mỗi lần gạt/tích chọn,
    // để tránh tái tạo lại tình trạng chậm do gọi shell liên tục)
    var descOnSh: String? = null

    // 值
    var value: String? = null
    var valueShell: String? = null
    var valueFromShell: String? = null
    var maxLength = -1 // input only
    var type: String? = null
    var max: Int = Int.MAX_VALUE // seekbar only
    var min: Int = Int.MIN_VALUE // seekbar only
    var required: Boolean = false // 是否是必需的
    // Giá trị readonly tĩnh (true/false/1/0) hoặc kết quả tạm thời (mặc định false) khi
    // readonlySh chưa được thực thi. Khi readonlySh có giá trị, readonly sẽ được ghi đè
    // bằng kết quả shell ngay khi mở dialog action (giống cơ chế của valueShell).
    var readonly: Boolean = false
    // Kịch bản shell kiểm tra readonly (nếu có, sẽ được gộp chạy cùng value-sh/options-sh
    // khi mở dialog action, giống valueShell - KHÔNG chạy ngay lúc parse trang nữa)
    var readonlySh: String? = null

    // ========== TÍNH NĂNG MỚI: DỒN MỤC readonly (TĨNH) XUỐNG DƯỚI (sort) ==========
    // Giống depend-sort nhưng dùng cho readonly TĨNH (khai báo readonly="true"/"false" hoặc
    // readonly="lệnh shell" qua readonlySh) thay vì depend-readonly (readonly phụ thuộc điều
    // kiện của param khác, thay đổi động trong lúc dialog đang mở).
    //
    // Mặc định: false (tắt) - giữ đúng thứ tự khai báo trong file cấu hình như trước đây.
    //
    // Khi bật (true): trong số các param có sort="true", param nào readonly=false (sáng, có
    // thể bấm/nhập) được dồn lên phía TRÊN CÙNG của nhóm; param nào readonly=true (xám, chỉ
    // đọc) bị dồn xuống phía DƯỚI CÙNG của nhóm. Thứ tự tương đối GIỮA các param cùng trạng
    // thái được giữ nguyên như thứ tự khai báo gốc (sắp xếp ổn định - stable sort). Vì readonly
    // tĩnh chỉ được xác định 1 lần trước khi dialog hiện ra (không đổi lại trong lúc dialog mở),
    // việc sắp xếp này cũng chỉ thực hiện ĐÚNG 1 LẦN lúc dựng layout - không animate.
    var sort: Boolean = false
    var options: ArrayList<SelectItem>? = null
    var optionsFromShell: ArrayList<SelectItem>? = null
    var optionsSh = ""
    // 是否允许多选(options 多选下拉; type=file/folder 时允许多选多个文件/文件夹)
    var multiple: Boolean = false
    // 是否支持
    var supported: Boolean = true
    // 文本框的水印（提示占位符）
    var placeholder: String = ""
    // Kịch bản shell sinh placeholder động
    var placeholderSh: String? = null
    // 文件mime类型（仅限type=file有效）
    var mime: String = ""
    // 文件后缀（仅限type=file有效），支持用逗号分隔多个后缀，例如 "zip,apk,7z"
    var suffix: String = ""
    // 打开文件/目录选择器时的初始目录（仅限type=file/folder有效），例如 "/sdcard/Android"
    // 用户仍然可以从这里返回到上一级目录
    var pathHome: String = ""
    // 是否允许用户手动输入路径
    var editable: Boolean = false
    // 多个值的分隔符（仅限多选下拉）
    var separator: String = "\n"

    // ========== 新增依赖管理功能 ==========

    // Tên (các) param điều khiển: param này sẽ ẩn/hiện dựa theo giá trị của (các) param có
    // "name" trùng dependOn. Có thể khai báo NHIỀU param cha cùng lúc, nối bằng dấu "|",
    // ví dụ: "mode|cam" -> phụ thuộc đồng thời vào cả param "mode" và "cam" (tất cả phải
    // cùng thỏa điều kiện tương ứng - AND).
    var dependOn: String? = null

    // Danh sách giá trị cần khớp cho từng param cha (theo đúng thứ tự khai báo ở dependOn),
    // các param cha cách nhau bởi dấu "|"; trong mỗi vị trí, các giá trị được chấp nhận (OR)
    // cách nhau bởi dấu phẩy, ví dụ: "a|b,c" -> cha 1 khớp khi = a, cha 2 khớp khi = b hoặc c.
    var dependValue: String? = null

    // "show": chỉ hiện khi khớp dependValue (mặc định) | "hide": ẩn khi khớp dependValue
    // Cũng có thể khai báo riêng cho từng param cha, nối bằng "|", theo đúng thứ tự dependOn,
    // ví dụ: "show|hide".
    var dependMode: String = "show"

    // Cách kết hợp nhiều điều kiện phụ thuộc (khi dependOn khai báo nhiều param cha):
    // - "and" (mặc định): TẤT CẢ điều kiện phải cùng thỏa (giữ tương thích hành vi cũ).
    // - "priority" (hoặc "or"): xét theo THỨ TỰ ưu tiên từ TRÁI SANG PHẢI theo đúng thứ tự
    //   khai báo trong dependOn. Điều kiện nào (đã tính cả dependMode của chính nó) thỏa
    //   trước sẽ quyết định luôn kết quả.
    //   Nếu không có điều kiện nào thỏa thì sử dụng dependDefault.
    // - "priority-rtl" (hoặc "or-rtl"): giống "priority" nhưng xét theo thứ tự ưu tiên từ
    //   PHẢI SANG TRÁI.
    // - "xor": chỉ ĐÚNG MỘT điều kiện phải thỏa.
    // - "nand": phủ định của "and" (không phải tất cả điều kiện đều thỏa).
    var dependLogic: String = "and"

    // ========== TÍNH NĂNG MỚI: MẶC ĐỊNH ẨN/HIỆN ==========
    // Giá trị mặc định khi KHÔNG có điều kiện phụ thuộc nào thỏa mãn:
    // - "show" (mặc định): hiển thị khi không có điều kiện nào khớp
    // - "hide": ẩn khi không có điều kiện nào khớp
    // Ví dụ: depend-on="mode" depend-value="advanced"
    //        depend-default="hide"
    //        -> Khi mode != advanced thì ẩn (không phải "show")
    var dependDefault: String = "show"

    // ========== TÍNH NĂNG MỚI: TRẠNG THÁI KHỞI ĐỘNG ==========
    // Trạng thái ẩn/hiện BAN ĐẦU khi chưa đánh giá bất kỳ điều kiện nào:
    // - "auto" (mặc định): tự động xác định dựa trên dependDefault
    // - "show": luôn hiển thị lúc đầu
    // - "hide": luôn ẩn lúc đầu
    // Hữu ích khi bạn không muốn param nhấp nháy lúc tải dialog.
    var dependInitialState: String = "auto"

    // ========== TÍNH NĂNG MỚI: ĐẢO NGƯỢC ĐIỀU KIỆN ==========
    // Nếu true, tất cả các điều kiện sẽ bị đảo ngược (NOT logic):
    // - "show" trở thành "hide"
    // - "hide" trở thành "show"
    // Ví dụ: depend-on="admin" depend-value="1" depend-negate="true"
    //        -> Hiện khi admin != 1 (ẩn khi admin = 1)
    var dependNegate: Boolean = false

    // ========== TÍNH NĂNG MỚI: NGƯỠNG ĐIỀU KIỆN (CHO "AND") ==========
    // Với logic "and", chỉ bao nhiêu % điều kiện cần thỏa mãn:
    // - -1 (mặc định): 100% (tất cả phải thỏa) - hành vi cũ
    // - 0-100: % số điều kiện cần thỏa, vd: 50 = ít nhất 50% điều kiện phải thỏa
    // Ví dụ: depend-on="a|b|c" 3 điều kiện
    //        depend-threshold="67" (tối thiểu 2/3 điều kiện)
    //        -> Chỉ cần tối thiểu 2 trong 3 điều kiện thỏa mãn
    var dependThreshold: Int = -1

    // ========== TÍNH NĂNG MỚI: HOẠT ĐỘNG KHÔNG ĐỒNG THỜI ==========
    // Nếu true, param ẩn sẽ vẫn được đưa vào kết quả readParamsValue() nếu nó có giá trị
    // (thay vì bỏ qua param ẩn). Hữu ích cho các param ẩn nhưng vẫn cần giá trị.
    var dependIncludeHidden: Boolean = true

    // ========== TÍNH NĂNG MỚI: CHA ẨN THÌ CON ẨN THEO ==========
    // Nếu true (mặc định), khi (bất kỳ) param cha trong depend-on đang bị ẨN (do chính
    // depend-on của nó, chuỗi phụ thuộc nhiều cấp...), param này CŨNG BỊ ẨN LUÔN, bất kể
    // giá trị hiện tại của cha có khớp depend-value hay không.
    // Nếu false: param KHÔNG bị ép ẩn toàn bộ chỉ vì 1 cha đang ẩn - thay vào đó, điều kiện
    // ứng với (các) cha ĐANG ẨN sẽ bị LOẠI HẲN khỏi phép kết hợp (and/or/priority/xor/...),
    // coi như cha đó "không áp dụng" ở thời điểm hiện tại; chỉ (các) cha ĐANG HIỆN mới được
    // dùng để tính. KHÔNG dùng lại giá trị cũ còn sót lại của cha đang ẩn (tránh trường hợp
    // giá trị cũ từ 1 nhánh loại trừ nhau vô tình khớp depend-value và làm sai kết quả).
    // Ví dụ: depend-on="dexlib|dexlibk" depend-cascade="false" - dexlib/dexlibk là 2 nhánh
    //        loại trừ nhau (chỉ 1 cha hiện tại 1 thời điểm) -> khi 1 trong 2 đang ẩn, chỉ cha
    //        còn lại (đang hiện) quyết định kết quả.
    var dependCascade: Boolean = true

    // ========== TÍNH NĂNG MỚI: LÀMĐIỀU GỌILẠI KHI THAY ĐỔI DEPENDENCY ==========
    // Tên shell script/callback để gọi khi param này thay đổi trạng thái ẩn/hiện
    // (chỉ gọi khi trạng thái thực sự thay đổi, từ visible -> hidden hoặc ngược lại)
    var dependOnChangeCallback: String? = null

    // ========== TÍNH NĂNG MỚI: CHỈ ĐỌC THAY VÌ ẨN (depend-readonly) ==========
    // Mặc định, khi điều kiện phụ thuộc đánh giá là "không thỏa" (shouldShow = false),
    // param sẽ bị ẨN HOÀN TOÀN (View.GONE) như hành vi cũ.
    // Nếu đặt dependReadonly = true, param KHÔNG bị ẩn nữa mà vẫn hiển thị bình thường,
    // nhưng sẽ bị làm MỜ (giảm alpha) và VÔ HIỆU HÓA (không thể bấm/nhập/chọn) - tức là
    // chuyển sang trạng thái "chỉ đọc" thay vì biến mất khỏi giao diện.
    // Khi điều kiện thỏa trở lại (shouldShow = true), param được bật lại bình thường
    // (hết mờ, có thể tương tác lại).
    // Ví dụ: depend-on="mode" depend-value="advanced" depend-readonly="true"
    //        -> Khi mode != advanced, param vẫn hiện nhưng bị mờ và khóa tương tác,
    //           thay vì biến mất như mặc định.
    var dependReadonly: Boolean = false

    // ========== TÍNH NĂNG MỚI: DỒN MỤC ĐANG KHÓA XUỐNG DƯỚI (depend-sort) ==========
    // CHỈ có tác dụng khi dependReadonly = true (bắt buộc phải đi kèm depend-readonly="true").
    // Nếu param khai báo depend-readonly="false" (hoặc không khai báo) mà vẫn đặt
    // depend-sort="true" thì depend-sort sẽ bị BỎ QUA (ép về false) - vì sort chỉ có ý nghĩa
    // với các mục "khóa tại chỗ" (vẫn hiện, chỉ mờ đi), không áp dụng được cho mục bị ẩn hẳn
    // (View.GONE không có khái niệm "dồn xuống dưới", vì nó không chiếm chỗ trên layout).
    //
    // Mặc định: false (tắt) - giữ đúng thứ tự khai báo trong file cấu hình như trước đây.
    //
    // Khi bật (true): trong số các param có depend-sort="true", param nào đang THỎA điều
    // kiện (sáng, có thể bấm/nhập) sẽ được dồn lên phía TRÊN CÙNG của nhóm; param nào đang
    // bị khóa (xám, depend-readonly kích hoạt vì điều kiện không thỏa) sẽ bị dồn xuống phía
    // DƯỚI CÙNG của nhóm. Thứ tự tương đối GIỮA các param cùng trạng thái (cùng sáng hoặc
    // cùng xám) được giữ nguyên như thứ tự khai báo gốc (sắp xếp ổn định - stable sort).
    // Các param KHÔNG khai báo depend-sort="true" (kể cả khi có depend-readonly) đứng yên,
    // không bị xáo trộn vị trí và cũng không "chiếm chỗ" của nhóm đang sort - nhóm sort chỉ
    // hoán đổi vị trí NỘI BỘ giữa các vị trí (slot) mà chính nhóm đó đang chiếm giữ.
    //
    // Ví dụ: 3 param cùng depend-readonly="true" depend-sort="true": A (đang sáng), B (đang
    // xám), C (đang sáng) -> sau khi sort: A, C, B (2 mục sáng dồn lên trên, B xám xuống cuối).
    var dependSort: Boolean = false

    // ========== TÍNH NĂNG MỚI: CHO PHÉP SPINNER ĐỂ TRỐNG (allow-no-selection) ==========
    // Chỉ áp dụng cho ParamsSingleSelect khi hiển thị dạng Spinner (options.size <= 6).
    // - false (MẶC ĐỊNH): giữ hành vi gốc của Android Spinner - luôn tự chọn sẵn mục đầu
    //   tiên trong danh sách nếu chưa có value/valueFromShell nào khớp (không hiện ô trống
    //   "Vui lòng chọn"). Phù hợp đa số trường hợp vì Spinner luôn cần có 1 giá trị hiệu lực.
    // - true: cho phép hiển thị trạng thái "chưa chọn gì" (ô trống + hint "Vui lòng chọn"),
    //   dùng khi thực sự cần phân biệt rõ giữa "người dùng chưa chọn" và "đã chọn mục đầu".
    // Ví dụ: allow-no-selection="true" (hoặc viết tắt: no-select="true")
    var allowNoSelection: Boolean = false

    // ========== TÍNH NĂNG MỚI: GHI NHỚ LỰA CHỌN (remember) ==========
    // Nếu true: giá trị người dùng chọn/nhập lần cuối (lúc bấm OK) sẽ được lưu lại (xem
    // ActionParamMemory), và lần sau mở lại dialog action sẽ TỰ NẠP giá trị đã nhớ đó làm mặc
    // định - ghi đè lên "value" tĩnh khai trong TOML.
    // Nếu param CÒN khai "value-sh" (đọc trạng thái thật từ hệ thống lúc mở dialog), kết quả
    // của value-sh vẫn được ưu tiên hơn giá trị đã nhớ, vì đó là trạng thái THẬT ngay lúc mở -
    // giá trị đã nhớ chỉ áp dụng khi value-sh không khai hoặc không trả về gì.
    // Mặc định: true (bật) - hầu hết trường hợp dùng spinner/select đều muốn nhớ lựa chọn cũ;
    // khai "remember = false" trong TOML nếu KHÔNG muốn nhớ (luôn quay về "value" tĩnh).
    var remember: Boolean = true
}