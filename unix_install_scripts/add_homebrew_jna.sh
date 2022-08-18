#!/bin/bash
# Updates jna paths for mac

usage() {
    echo "Usage: add_homebrew_jna.sh [-c config_file]" 1>&2
}

while getopts "c:" o; do
    case "${o}" in
    c)
        append_path=${OPTARG}
        ;;
    *)
        usage
        exit 1
        ;;
    esac
done

if [[ -z "$append_path" ]]; then
    usage
    exit 1
fi

gstreamer_paths=$({ brew deps --installed gst-plugins-base gst-plugins-good gstreamer; echo -e "gst-plugins-base\ngst-plugins-good\ngstreamer" ; } \
    | sort \
    | uniq \
    | xargs brew ls \
    | grep /lib/ \
    | xargs -I{} dirname {} \
    | sort \
    | uniq \
    | sed -e :a -e '$!N; s/\n/:/; ta')

echo -e "\njreflags=\"\$jreflags -Djna.library.path=\\\"$gstreamer_paths\\\"\"" >> $append_path

