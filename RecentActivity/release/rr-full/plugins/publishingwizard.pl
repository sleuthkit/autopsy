#-----------------------------------------------------------
# publishingwizard.pl
#   Extract Extract AddNetPlace\\LocationMRU
#
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#
# copyright (c) 2011-02-02 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package publishingwizard;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20110202);
my $VERSION = getVersion();

# Functions #
sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extract AddNetPlace\\LocationMRU for Microsoft Publishing Wizard";
}
sub getRefs {
	my %refs = ("Microsoft Publishing Wizard Homepage:" =>
	            "http://www.microsoft.com/downloads/details.aspx?FamilyId=56E5B1C5-BF17-42E0-A410-371A838E570A");
	return %refs;
}

############################################################
# pluginmain #
############################################################
sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching publishingwizard v.".$VERSION);
    ::rptMsg("publishingwizard v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\PublishingWizard\\AddNetworkPlace\\AddNetPlace\\LocationMRU";

	# If # Publishing Wizard path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		::rptMsg("Publishing Wizard");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		# Extract # all keys from Publishing Wizard registry path #
		my %keys;
		my @vals = $key->get_list_of_values();

		# If # registry keys exist in path #
		if (scalar(@vals) > 0) {

			# Extract # all key names+values for Publishing Wizard registry path #
			foreach my $v (@vals) {
				$keys{$v->get_name()} = $v->get_data();
			}

			# Return # all key names+values #
			foreach (sort keys %keys) {
				::rptMsg($_." -> ".$keys{$_});
			}

		# Error # key value is null #
		} else {
			::rptMsg($key_path." has no values.");
		}

	# Error # Publishing Wizard isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
