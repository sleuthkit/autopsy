#-----------------------------------------------------------
# netassist.pl
#   Plugin to determine if a system is infected with the BHO "My.Freeze.com".
#   This is a BHO specifically for firefox and is installed as an addon using a
#   third party installer. This is usually done when a user installs a product
#   and is installed without the user reading all the information on the install.
#   It usually requires the user to uncheck a box but as most users do not read
#   everything it is installed unknowingly.
#   If you look under the "addons" in firefox you will see an addon called
#   "Freeze.com Net Assistant for Firefox", but you can only enable or disable
#   it from there.  To uninstall it completely from #the system you must
#   uninstall from the system "add/remove" program under the control panel.
#
# Change history
#   20110427 [mmo] % created
#   20110830 [fpi] + banner, no change to the version number
#
# References
#    
# Script written by Mark Morgan
#-----------------------------------------------------------
# Require #
package netassist;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20110427);
my $VERSION = getVersion();

# Functions #
sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Check for Firefox Extensions.";
}
sub getRefs {
	my %refs = ("");
	return %refs;	
}

############################################################
# pluginmain #
############################################################
sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;
	my @interesting_paths = (
    'Software\\Mozilla\\Firefox\\Extensions',
    'Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\NetAssistant',
    'Software\\Microsoft\\Installer\\Products\\D4676621F4CF7AF46BB388D4351B86F0',
    'Software\\Microsoft\\Installer\\Products\\D4676621F4CF7AF46BB388D4351B86F0\\SourceList',
    
	);
	my @interesting_keys = (
		"Values",
		"ValueViewOnly"
	);

	# Initialize #
	::logMsg("Launching netassist v.".$VERSION);
    ::rptMsg("netassist v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	# Extract # possible registry paths
	foreach my $key_path (@interesting_paths) {

		# If # WinVNC path exists #
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {

		::rptMsg("netassist");
		::rptMsg($key_path);
		::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
		::rptMsg("");
		my %keys;
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-12s %-20s",$v->get_name(),$v->get_data());
			}
			
		# Return # all key names+values for interesting keys #
			foreach my $var (@interesting_keys) {
				if (exists $keys{$var}) {
					my $hstring = unpack ("H*",$keys{$var});
						::rptMsg($var." -> ".$hstring);
					}
				}

				# Return # obligatory new-line #
				::rptMsg("");

			# Error # key value is null #
			} else {
				::rptMsg($key_path." has no values.");
			}

		# Error # WinVNC isn't here, try another castle #
		} else {
			::rptMsg($key_path." not found.");
			::logMsg($key_path." not found.");
		}

	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
