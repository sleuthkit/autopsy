#-----------------------------------------------------------
# regback.pl
#   Plugin to assist to determine if a registry backup was executed and
#   provide the key name of the log file which is located at
#   Windows/System32/logfiles/Scm/.
#   It will then go out and list all tasks scheduled through the 
#   task scheduler along with the name of each log file associated 
#   with that task.  It will then print out the last written time and date.
#   This is for Windows NT systems ONLY (Vista, Win 7, 2008) blog post
#
# Change History:
#   20110427 [mmo] % created
#   20110830 [fpi] + banner, no change to the version number
#
# References
#   http://dfsforensics.blogspot.com/2011/03/interesting-regsitry-backup-feature-of.html
#
# Script written by Mark Morgan
#-----------------------------------------------------------
package regback;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100219);

sub getConfig{return %config}

sub getShortDescr {
	return "Get logfile name of registry backup tasks";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {

	::logMsg("Launching regback v.".$VERSION);
  ::rptMsg("regback v.".$VERSION); # 20110830 [fpi] + banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # 20110830 [fpi] + banner

	my $class = shift;
	my $hive = shift;
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Schedule\\TaskCache\\Tree\\Microsoft\\Windows\\Registry\\RegIdleBackup";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("regidle");
		::rptMsg($key_path);
		::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-12s %-20s",$v->get_name(),$v->get_data());
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
	my $class = shift;
	my $hive = shift;
	
	my %tasks;
	
sub getShortDescr {
	return "List all tasks along with logfile name and last written date/time";	
}
	
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Schedule\\TaskCache\\Tasks";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $path;
				eval {
					$path = $s->get_value("Path")->get_data();
					::rptMsg("Path      	: ".$path);
					::rptMsg("Dynamicinfo  	: ".$s->get_name());
					::rptMsg("LastWrite : ".gmtime($s->get_timestamp())." (UTC)");
					::rptMsg("");
				};
				
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
}


1;