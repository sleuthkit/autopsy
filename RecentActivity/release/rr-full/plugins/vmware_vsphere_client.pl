#-----------------------------------------------------------
# vmware_vsphere_client.pl
#   Extract recent connections list for VMware vSphere Client
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# copyright (c) 2011-02-04 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package vmware_vsphere_client;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20110204);
my $VERSION = getVersion();

# Functions #
sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extract recent connections list for VMware vSphere Client.";
}
sub getRefs {
	my %refs = ("VMware vSphere Client Homepage:" =>
	            "http://www.vmware.com/products/vsphere/");
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
    'Software\\VMware\\Virtual Infrastructure Client\\Preferences\\UI\\ClientsXml',
    'Software\\VMware\\VMware Infrastructure Client\\Preferences'
	);

	# Initialize #
	::logMsg("Launching vmware_vsphere_client v.".$VERSION);
    ::rptMsg("vmware_vsphere_client v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	# Extract # possible registry paths
	foreach my $key_path (@interesting_paths) {

		# If # VMware vSphere Client path exists #
		my $xml;
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {

			# Return # plugin name, registry key and last modified date #
			::rptMsg("VMware vSphere Client");
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");

			# Extract # all keys from VMware vSphere Client registry path #
			my @vals = $key->get_list_of_values();

			# If # registry keys exist in path #
			if (scalar(@vals) > 0) {

				# Return # all key names+values for VMware vSphere Client registry path #
				foreach my $v (@vals) {
				  # Format # XML data with no new line characters
					$xml = $v->get_data();
					$xml =~ s/>\s*\r*\n*/>/g;
					::rptMsg($v->get_name()." -> ".$xml);
				}
				# Return # obligatory new-line #
				::rptMsg("");

			# Error # key value is null #
			} else {
				::rptMsg($key_path." has no values.");
			}

		# Error # VMware vSphere Client isn't here, try another castle #
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
