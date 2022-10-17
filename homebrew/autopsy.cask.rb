cask "autopsy" do
    version "4.19.3"
    # sha256 "a33aca4dd6686b1dba790b224c9e686d7e08c86e6074379194f3bde478d883ed"
    url "https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.19.2/autopsy-4.19.2.zip"
    appcast "https://github.com/sleuthkit/autopsy/releases.atom"
    name "autopsy"
    desc "Minimal installer for conda specific to conda-forge"
    homepage "https://github.com/conda-forge/miniforge"
  
    auto_updates true
    conflicts_with cask: "miniconda"
    container type: :naked
  
    installer script: {
      executable: "Miniforge3-#{version}-MacOSX-x86_64.sh",
      args:       ["-b", "-p", "#{caskroom_path}/base"],
    }
    binary "#{caskroom_path}/base/condabin/conda"
  
    uninstall delete: "#{caskroom_path}/base"
  
    zap trash: [
      "~/.condarc",
      "~/.conda",
    ]
  
    caveats <<~EOS
      Please run the following to setup your shell:
        conda init "$(basename "${SHELL}")"
    EOS
  end