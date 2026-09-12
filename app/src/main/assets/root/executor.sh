#!/data/data/com.tool.tree/files/home/bin/bash
# Kakathic

export EXECUTOR_PATH="$({EXECUTOR_PATH})"
export START_DIR="$({START_DIR})"
export TEMP="$({TEMP_DIR})"
export ANDROID_UID="$({ANDROID_UID})"
export API="$({ANDROID_SDK})"
export SDCARD_PATH="$({SDCARD_PATH})"
export PACKAGE_NAME="$({PACKAGE_NAME})"
export PACKAGE_VERSION_NAME="$({PACKAGE_VERSION_NAME})"
export PACKAGE_VERSION_CODE="$({PACKAGE_VERSION_CODE})"
export APP_USER_ID="$({APP_USER_ID})"
export APP_UID="$({APP_UID})"
export PATH_APK="$({PATH_APK})"
export DARK_MODE="$({DARK_MODE})"
export KERNEL_VERSION="$({KERNEL_VERSION})"
export CPU_ABI="$({CPU_ABI})"
export LANGUAGE="$({LANGUAGE})"
export COUNTRY="$({COUNTRY})"
export TIMEZONE="$({TIMEZONE})"
export ANDROID_DEVICE="$({ANDROID_DEVICE})"
export ANDROID_BRAND="$({ANDROID_BRAND})"
export ANDROID_MANUFACTURER="$({ANDROID_MANUFACTURER})"
export ANDROID_FINGERPRINT="$({ANDROID_FINGERPRINT})"
export ANDROID_RELEASE="$({ANDROID_RELEASE})"
export ANDROID_MODEL="$({ANDROID_MODEL})"
export ANDROID_ID="$({ANDROID_ID})"
export ROOT=$({ROOT_PERMISSION})
export ARCH=$({ARCH})
export HOME="$({TOOLKIT})"
export TERMUX="$HOME/termux"
export ETC="$HOME/etc"
export BIN="$HOME/bin"
export LOG="$HOME/usr/log"
export AON="$HOME/ZADD"
export AOK="$HOME/ZAPK"
export TMPDIR="$HOME/tmp"
export LIB="$HOME/lib"
export TMP="$TMPDIR"
export JAVA_HOME="$TERMUX"
export PIP_ROOT_USER_ACTION=ignore
export COLORTERM=truecolor
export PATH="$BIN:$TERMUX/bin:$TERMUX/py:$PATH"
export TERM=xterm-256color
export START_TIME="$(date +%s)"

# export LD_LIBRARY_PATH="$LIB"
export SDC="$(glog SDC $SDCARD_PATH/TREE)";
export SDH="$(glog SDH $HOME/TREE)";
export APK="$(glog APK $HOME/TOOL)";
export PTSD="$(glog PTSD $SDC/ROM)";
export PTAD="$(glog PTAD $SDC/APK)";
export PTSH="$(glog PTSH ROM)"; # $SDH/$PTSH
export PTAH="$(glog PTAH APK)"; # $APK/$PTAH

# Không giới hạn ngang
[ "$(glog scroll_ngang)" == 1 ] || export COLUMNS=47

# User web
export WEBS="User-Agent: Mozilla/5.0 (Linux; Android $ANDROID_RELEASE; $ANDROID_MODEL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

if [ "$ROOT" == 'true' ]; then
    export ROT=1
    export LOT=0
    else
    export ROT=0
    export LOT=1
    export xu=xu
    export text_root="ROOT"
fi

if [ "$ARCH" != 'aarch64' ] && [ "$ARCH" != 'armv8l' ]; then
    text_error="Only arm64-v8a, armv8l devices supported"
    showtoast --am "$text_error"
    echo "$text_error" >&2
    sleep 10
    echo "exit:[kill]"
    exit 1
fi

# Giới hạn cpu (dùng hex mask chọn nhân lớn cho Toybox)
if command -v taskset &>/dev/null; then
    max_cpukkk="$(nproc --all 2>/dev/null)"
    use_cpukkk="$(glog use_cpu $(( max_cpukkk / 2 )))"
    if [ -n "$use_cpukkk" ] && [ "$use_cpukkk" -lt $max_cpukkk ]; then
    maskkkk=$(( ((1 << use_cpukkk) - 1) << (max_cpukkk - use_cpukkk) ))
    taskset -p "$(printf "%x" $maskkkk)" $$ &>/dev/null
    fi
fi

if [ -f "$1" ]; then
    export shell_progres="$2";
    cd "$HOME";
    source "$1";
    rm -f "$1";
    exit 0
    else
    if [ "$1" ]; then
    echo "Error file" >&2
    sleep 5
    fi
fi
