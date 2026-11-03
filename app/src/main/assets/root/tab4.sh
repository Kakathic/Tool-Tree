if [ "$(glog Toff)" != 1 ]; then
if [ "$(glog Tset)" == 2 ]; then
echo "$ETC/toml/boxset.toml"
else
echo "$ETC/toml/settings.toml"
fi
fi