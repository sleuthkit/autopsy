# Documentation: https://docs.brew.sh/Formula-Cookbook
#                https://rubydoc.brew.sh/Formula
class Autopsy < Formula
  desc "Autopsy® is a digital forensics platform and graphical interface to The Sleuth Kit® and other digital forensics tools. It can be used by law enforcement, military, and corporate examiners to investigate what happened on a computer. You can even use it to recover photos from your camera's memory card. "
  homepage "http://www.sleuthkit.org/autopsy/"
  url "https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.19.2/autopsy-4.19.2.zip"
  sha256 "b1ca770df47f09512276fee16c184644cdd9a2591edfdb622a3177896f299893"
  license "Apache-2.0"

  depends_on "afflib"
  depends_on "libewf"
  #depends_on "libpq"

  depends_on "testdisk"
  # TODO is this right?
  depends_on "gstreamer"
  depends_on "libheif"

  depends_on "libtool" => :build
  depends_on "autoconf" => :build
  depends_on "automake" => :build
  depends_on "zip" => :build
  depends_on "gnu-tar" => :build
  depends_on "ant" => :build

  resource "sleuthkit" do
    url "https://github.com/sleuthkit/sleuthkit/releases/download/sleuthkit-4.11.0/sleuthkit-4.11.0.tar.gz"
    #TODO sha256 "1795bd85fb05348c32dc3e915bc8aeaa7efeeaf849676f8be88b3c9aaf3799dd"
  end

  # sha256 calculated using curl <url> | sha256sum
  # TODO could create separate for build and run
  on_linux do
    depends_on "sqlite"

    on_arm do 
      resource "liberica_jvm" do
        url "https://download.bell-sw.com/java/8u345+1/bellsoft-jre8u345+1-linux-aarch64-full.tar.gz"
        #TODO sha256 "1795bd85fb05348c32dc3e915bc8aeaa7efeeaf849676f8be88b3c9aaf3799dd"
      end
    end
    on_intel do
      resource "liberica_jvm" do
        url "https://download.bell-sw.com/java/8u345+1/bellsoft-jre8u345+1-linux-amd64-full.tar.gz"
        #sha256 "70899945312ee630190b8c4f9dc1124af91fec14987e890074bda40126ec186e"
      end
    end
  end
  on_macos do
    uses_from_macos "sqlite"

    on_arm do 
      resource "liberica_jvm" do
        url "https://download.bell-sw.com/java/8u345+1/bellsoft-jre8u345+1-macos-aarch64-full.tar.gz"
        #TODO sha256 "58a99eadac9834db01ea96bcd2e47795ace073ecae4783055a1979f099e65d9f"
      end
    end
    on_intel do
      resource "liberica_jvm" do
        url "https://download.bell-sw.com/java/8u345+1/bellsoft-jre8u345+1-macos-amd64-full.tar.gz"
        #TODO sha256 "74348b0f7549056da898a83d3d582588cb12718bcf28c3ccbd1850877eb4c7ec"
      end
    end
  end

  def install
    ENV.deparallelize
    install_dir = File.join(prefix, "install")

    # ----- SETUP JVM -----
    jvm_bin_path = File.join(install_dir, "liberica_jvm")
    system "mkdir", "-p", jvm_bin_path
    resource("liberica_jvm").stage(jvm_bin_path)
    ENV["JAVA_HOME"] = jvm_bin_path
    ENV["ANT_FOUND"] = Formula["ant"].opt_bin/"ant"

    # ----- SETUP TSK -----
    sleuthkit_bin_path = File.join(install_dir, "sleuthkit")
    system "mkdir", "-p", sleuthkit_bin_path
    resource("sleuthkit").stage(sleuthkit_bin_path)
    cd sleuthkit_bin_path do
        ENV.append_to_cflags "-DNDEBUG"
        system "./bootstrap"
        system "./configure", "--disable-dependency-tracking", "--prefix=#{prefix}"
        system "make"
        system "make", "install"
    end
    ENV["TSK_HOME"]= sleuthkit_bin_path
    
    # ----- RUN UNIX SETUP SCRIPT -----
    autopsy_tmp_path = `find $(pwd) -maxdepth 1 -type d -name "autopsy-*.*.*"`.strip()
    autopsy_install_path = File.join(install_dir, "autopsy")
    system "mv", autopsy_tmp_path, autopsy_install_path
    
    unix_setup_script = File.join(autopsy_install_path, "unix_setup.sh")

    # TODO remove for the future
    system "rm", unix_setup_script
    system "curl", "-o", unix_setup_script, "https://raw.githubusercontent.com/gdicristofaro/autopsy/8425_linuxMacBuild/unix_setup.sh"

    system "chmod", "u+x", unix_setup_script

    ENV["TSK_JAVA_LIB_PATH"] = File.join(prefix, "share", "java")
    java_path = "#{jvm_bin_path}/bin/java"
    system unix_setup_script, "-j", "#{java_path}"

    system "ln", "-s", File.join(autopsy_install_dir, "bin", "autopsy"), File.join(bin, "autopsy") 
    
    # TODO do we need env variables before execution? (i.e. LD PATH)
  end

  test do
    system "#{bin}/autopsy", "--help"
  end
end
