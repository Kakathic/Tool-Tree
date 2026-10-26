# Kakathic
if [ "$(glog Tset)" == 1 ]; then
echo "$ETC/toml/home.toml"
elif [ "$(glog Tset)" == 2 ]; then
echo "$ETC/toml/boxmore.toml"
else
echo "$ETC/toml/more.toml"
fi
