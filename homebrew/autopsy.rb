# Documentation: https://docs.brew.sh/Formula-Cookbook
#                https://rubydoc.brew.sh/Formula
# PLEASE REMOVE ALL GENERATED COMMENTS BEFORE SUBMITTING YOUR PULL REQUEST!
class Autopsy < Formula
  desc "Autopsy® is a digital forensics platform and graphical interface to The Sleuth Kit® and other digital forensics tools. It can be used by law enforcement, military, and corporate examiners to investigate what happened on a computer. You can even use it to recover photos from your camera's memory card. "
  homepage "http://www.sleuthkit.org/autopsy/"
  url "https://github.com/sleuthkit/autopsy/archive/refs/tags/autopsy-4.19.3.tar.gz"
  # sha256 "67299005603af0cadc98c420ce5088187010b71eabcbb6db7a4e5bce325734c5"
  license "Apache-2.0"

  depends_on "postgresql@14"
  depends_on "testdisk"
  depends_on "sleuthkit"
  # TODO is this right?
  depen# ds_on "gstreamer"
  depends_on "zip" => :build
  depends_on "tar" => :build
  depends_on "ant" => :build

  def install
    ENV.deparallelize
    # ----- GET ADDITIONAL DEPENDENCIES -----
    # TODO may be a better way to handle this
    # system "brew", "tap", "bell-sw/liberica"
    # system "brew", "install", "--cask", "liberica-jdk8-full"
    # jvm_tar_name = "bellsoft-jdk8u345+1-macos-amd64.tar.gz"
    # jvm_tmp_path = "#{prefix}/#{jvm_tar_name}"
    # jvm_bin_path = "#{prefix}/liberica_jvm"
    # system "curl", "-k", "-o", jvm_tmp_path, "https://download.bell-sw.com/java/8u345+1/#{jvm_tar_name}"
    # system "mkdir", "-p", jvm_bin_path
    # system "tar", "-xf", "#{prefix}/#{jvm_tar_name}", "-C", jvm_bin_path
    # # TODO do we need any permissions?
    # system "rm", jvm_tmp_path

    jvm_pkg_name = "bellsoft-jdk8u345+1-macos-amd64.pkg"
    jvm_tmp_path = "#{prefix}/#{jvm_pkg_name}"
    jvm_bin_path = "#{prefix}/liberica_jvm"
    system "curl", "-k", "-o", jvm_tmp_path, "https://download.bell-sw.com/java/8u345+1/bellsoft-jdk8u345+1-macos-amd64.pkg"
    system "mkdir", "-p", jvm_bin_path
    system "installer", "-pkg", jvm_tmp_path, "-target", jvm_bin_path
    system "rm", jvm_tmp_path

    # TODO may be a better way to handle this
    gstreamer_tmp_path = "#{prefix}/gstreamer-1.0-1.20.3-universal.pkg"
    gstreamer_bin_path = File.join(prefix, "gstreamer", "bin")
    system "curl", "-k", "-o", gstreamer_tmp_path, "https://gstreamer.freedesktop.org/data/pkg/osx/1.20.3/gstreamer-1.0-1.20.3-universal.pkg"
    system "mkdir", "-p", gstreamer_bin_path
    system "installer", "-pkg", gstreamer_tmp_path, "-target", gstreamer_bin_path
    system "rm", gstreamer_tmp_path

    # ----- BUILD ZIP -----
    autopsy_src_path = `pwd`
    java_path = `#{jvm_bin_path}/bin/java`

    netbeans_plat_ver = `grep "netbeans-plat-version=" "$AUTOPSY_SRC_PATH/nbproject/platform.properties" | cut -d'=' -f2`
    autopsy_platform_path = File.join(autopsy_src_path, "netbeans-plat", netbeans_plat_ver)
    autopsy_harness_path = File.join(autopsy_platform_path, "harness")

    ENV["JAVA_HOME"] = java_path
    ENV["TSK_HOME"]= `brew --prefix sleuthkit`
    system "ant", "-Dnbplatform.active.dir=\"#{autopsy_platform_path}\"", "-Dnbplatform.default.harness.dir=\"#{autopsy_harness_path}\"", "build", "build-zip"

    # ----- SETUP EXTRACT DIRECTORY -----
    autopsy_zip = `find #{autopsy_src_path}/dist -maxdepth 1 -name "autopsy-*.*.*.zip"`
    system "unzip", autopsy_zip, "-d", File.join(autopsy_src_path, "dist")
    autopsy_install_dir = `find #{File.join(autopsy_src_path, "dist")} -maxdepth 1 -type d -name "autopsy-*.*.*"`
    
    # ----- RUN UNIX SETUP SCRIPT -----
    unix_setup_script = File.join(autopsy_install_dir, "unix_setup.sh")
    system "chmod", "u+x", unix_setup_script

    base_sleuthkit_path = `brew --prefix sleuthkit`   
    ENV["TSK_JAVA_LIB_PATH"] = File.join(base_sleuthkit_path, "share", "java")
    system "bash", "-c", "cd \"#{autopsy_install_dir}\" && ./unix_setup.sh -j \"#{java_path}\""

    # TODO do we need to symlink binary?
    # TODO do we need env variables before execution?
  end

  test do
    system "#{bin}/autopsy", "--help"
  end
end
