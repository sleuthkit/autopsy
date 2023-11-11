# Documentation: https://docs.brew.sh/Formula-Cookbook
#                https://rubydoc.brew.sh/Formula

# Named Autopsy4 to avoid conflict with the autopsy formula in repos
# A package installer can be generated using brew-pkg: https://github.com/timsutton/brew-pkg
# Can be run locally with `brew install --debug --build-from-source --verbose <path_to_this_file>`
# sha256 calculated using curl <url> | sha256sum
class Autopsy4 < Formula
  AUTOPSY_RESOURCE_URL = "https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.20.0/autopsy-4.20.0.zip".freeze
  AUTOPSY_RESOURCE_SHA256 = "60964AB135429C2636AB8A1B0DA5EE18D232D4323DB6EDE1B6A9CFBF7E3500CE".freeze
  TSK_RESOURCE_URL = "https://github.com/sleuthkit/sleuthkit/releases/download/sleuthkit-4.12.0/sleuthkit-4.12.0.tar.gz".freeze
  TSK_RESOURCE_SHA256 = "0FAE8DBCCA69316A92212374272B8F81EFD0A669FB93D61267CFD855B06ED23B".freeze

  desc "Autopsy® is a digital forensics platform and graphical interface to The Sleuth Kit® and other digital forensics tools. It can be used by law enforcement, military, and corporate examiners to investigate what happened on a computer. You can even use it to recover photos from your camera's memory card. "
  homepage "http://www.sleuthkit.org/autopsy/"

  url AUTOPSY_RESOURCE_URL
  sha256 AUTOPSY_RESOURCE_SHA256
  license "Apache-2.0"

  depends_on "afflib"
  depends_on "libewf"

  depends_on "testdisk"

  depends_on "libheif"
  
  depends_on "openjdk@17"
  depends_on "gst-libav"
  depends_on "gst-plugins-bad"
  depends_on "gst-plugins-base"
  depends_on "gst-plugins-good"
  depends_on "gst-plugins-ugly"
  depends_on "gstreamer"

  depends_on "libtool" => :build
  depends_on "autoconf" => :build
  depends_on "automake" => :build
  depends_on "zip" => :build
  depends_on "gnu-tar" => :build
  depends_on "ant" => :build

  resource "sleuthkit" do
    url TSK_RESOURCE_URL
    sha256 TSK_RESOURCE_SHA256
  end

  # sha256 calculated using curl <url> | sha256sum
  # TODO could create separate for build and run
  on_linux do
    depends_on "sqlite"
  end
  on_macos do
    uses_from_macos "sqlite"
  end

  conflicts_with "ffind", because: "both install a `ffind` executable"
  conflicts_with "sleuthkit", because: "both install sleuthkit items"
  conflicts_with "autopsy", because: "both install sleuthkit items and have autopsy executables"

  def install
    ENV.deparallelize
    install_dir = File.join(prefix, "install")

    # ----- SETUP JVM -----
    java_home_path = "#{Formula["gstreamer"].prefix}/opt/openjdk@17"
    ENV["JAVA_HOME"] = java_home_path
    ENV["PATH"] = "#{java_home_path}/bin:#{ENV["PATH"]}"
    ENV["ANT_FOUND"] = Formula["ant"].opt_bin/"ant"
    
    # ----- SETUP TSK -----
    sleuthkit_bin_path = File.join(install_dir, "sleuthkit")
    system "mkdir", "-p", sleuthkit_bin_path
    resource("sleuthkit").stage(sleuthkit_bin_path)
    cd sleuthkit_bin_path do
        system "./configure", "--disable-dependency-tracking", "--prefix=#{prefix}"
        system "make"
        system "make", "install"
    end
    
    # ----- RUN UNIX SETUP SCRIPT -----
    autopsy_tmp_path = `find $(pwd) -maxdepth 1 -type d -name "autopsy-*.*.*"`.strip()
    autopsy_install_path = File.join(install_dir, "autopsy")
    system "cp", "-a", "#{autopsy_tmp_path}/.", autopsy_install_path
    
    unix_setup_script = File.join(autopsy_install_path, "unix_setup.sh")
    system "chmod", "a+x", unix_setup_script

    ENV["TSK_JAVA_LIB_PATH"] = File.join(prefix, "share", "java")
    system unix_setup_script, "-j", "#{java_home_path}"

    open(File.join(autopsy_install_path, "etc", "autopsy.conf"), 'a') { |f|
      # gstreamer needs the 'gst-plugin-scanner' to locate gstreamer plugins like the ones that allow gstreamer to play videos in autopsy
      # so, the jreflags allow the initial gstreamer lib to be loaded and the  'GST_PLUGIN_SYSTEM_PATH' along with 'GST_PLUGIN_SCANNER'
      # allows gstreamer to find plugin dependencies
      f.puts("export jreflags=\"-Djna.library.path=/usr/local/lib $jreflags\"") 
      f.puts("export GST_PLUGIN_SYSTEM_PATH=\"/usr/local/lib/gstreamer-1.0\"")
      f.puts("export GST_PLUGIN_SCANNER=\"#{Formula["gstreamer"].prefix}/libexec/gstreamer-1.0/gst-plugin-scanner\"")
    }

    bin_autopsy = File.join(bin, "autopsy")
    system "ln", "-s", File.join(autopsy_install_path, "bin", "autopsy"), bin_autopsy
  end

  test do
    system "#{bin}/autopsy", "--help"
  end
end
