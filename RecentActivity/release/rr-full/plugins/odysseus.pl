#-----------------------------------------------------------
# odysseus.pl
#   Extract registry keys for Odysseus by bindshell.net
# 
# Change history
#   20110830 [fpi] + banner, no change to the version number
#
# References
#   http://blogs.technet.com/b/markrussinovich/archive/2011/03/08/3392087.aspx
#
# copyright (c) 2011-02-02 Brendan Coles <bcoles@gmail.com>
#-----------------------------------------------------------
# Require #
package odysseus;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20110202);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Extract registry keys for Odysseus by bindshell.net.";
}
sub getDescr {
	return 'Extracts the following registry keys for Odysseus by'.
	' bindshell.net : "ProxyUpstreamHost","ProxyUpstreamPort",'.
	'"ProxyPort","ServerCert","ServerCertPass"';
}
sub getRefs {
	my %refs = ("Odysseus Homepage:" =>
	            "http://www.bindshell.net/tools/odysseus");
	return %refs;	
}

############################################################
# pluginmain #
############################################################
sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;
	my @interesting_keys = (
		"ProxyUpstreamHost",
		"ProxyUpstreamPort",
		"ProxyPort",
		"ServerCert",
		"ServerCertPass"
	);

	# Initialize #
	::logMsg("Launching odysseus v.".$VERSION);
    ::rptMsg("odysseus v.".$VERSION); # 20110830 [fpi] + banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\bindshell.net\\Odysseus";

	# If # odysseus path exists #
	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		::rptMsg("Odysseus");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		# Extract # all keys from Odysseus registry path #
		my %keys;
		my @vals = $key->get_list_of_values();

		# If # registry keys exist in path #
		if (scalar(@vals) > 0) {

			# Extract # all key names+values for Odysseus registry path #
			foreach my $v (@vals) {
				$keys{$v->get_name()} = $v->get_data();
			}

			# Return # all key names+values for interesting keys #
			foreach my $var (@interesting_keys) {
				if (exists $keys{$var}) {
					::rptMsg($var." -> ".$keys{$var});
				}
			}

		# Error # key value is null #
		} else {
			::rptMsg($key_path." has no values.");
		}

	# Error # Odysseus isn't here, try another castle #
	} else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}

	# Return # obligatory new-line #
	::rptMsg("");
}

# Error # oh snap! #
1;
