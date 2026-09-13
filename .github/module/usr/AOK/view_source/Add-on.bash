# Add-on
id=view_source
name="View source code"
author=Kakathic
description="Information and source code"
version=1.0
versionCode=100
root=false

# default
google_text="Currently using a translation tool"
view_source_text_5="Input file"
view_source_text_6="Import file"
view_source_text_7="Do not decompile source code"
view_source_text_8="Do not decode resources"
view_source_text_9="Options"
view_source_text_10="Please note that in most cases, jadx cannot decompile 100% of the source code, so errors will occur."
view_source_text_11="Dex to Java Decompiler"
view_source_text_12="Save to"
view_source_text_13="Decompilation error!"
view_source_text_14="Download"
view_source_text_15="Lack of jadx resources"
view_source_text_16="Reset data"

# other languages
case "$LANGUAGE-$COUNTRY" in
  vi*)
    name="Xem mã nguồn"
    description="Thông tin và mã nguồn"
    view_source_text_5="Tệp đầu vào"
    view_source_text_6="Nhập tệp tin"
    view_source_text_7="Không dịch ngược mã nguồn"
    view_source_text_8="Không giải mã tài nguyên"
    view_source_text_9="Tùy chọn"
    view_source_text_10="Xin lưu ý rằng trong hầu hết các trường hợp, jadx không thể dịch ngược toàn bộ 100% mã nguồn, do đó sẽ xảy ra lỗi."
    view_source_text_11="Trình dịch ngược Dex sang Java"
    view_source_text_12="Lưu ở"
    view_source_text_13="Lỗi dịch ngược !"
    view_source_text_14="Tải về"
    view_source_text_15="Thiếu tài nguyên của jadx"
    view_source_text_16="Cài lại dữ liệu"
    ;;
  *)
    ;;
esac
