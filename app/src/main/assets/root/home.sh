# Kakathic
if [ "$(glog Tset)" == 1 ]; then
echo "$ETC/toml/more.toml"
elif [ "$(glog Tset)" == 2 ]; then
echo "$ETC/toml/boxapp.toml"
else
echo "$ETC/toml/home.toml"
fi
