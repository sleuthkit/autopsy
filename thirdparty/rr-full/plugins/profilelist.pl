#-----------------------------------------------------------
# profilelist.pl
# Gets ProfileList subkeys and ProfileImagePath value
#
# History:
#   20200922 - MITRE update
#   20200518 - updated date output format
#   20100219 - updated to gather SpecialAccounts and domain
#              user info
#   20080415 - created
#
#
# copyright 2020 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package profilelist;
use strict;

my %config = (hive          => "software",
              MITRE         => "",
              category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200922);

sub getConfig{return %config}

sub getShortDescr {
	return "Get content of ProfileList key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	my %profiles;
	
	::logMsg("Launching profilelist v.".$VERSION);
	::rptMsg("profilelist v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\ProfileList";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $path;
				eval {
					$path = $s->get_value("ProfileImagePath")->get_data();
				};
				
				::rptMsg("Path      : ".$path);
				::rptMsg("SID       : ".$s->get_name());
				::rptMsg("LastWrite : ".::format8601Date($s->get_timestamp())."Z");
				
				my $user;
				if ($path) {
					my @a = split(/\\/,$path);
					my $end = scalar @a - 1;
					$user = $a[$end];
					$profiles{$s->get_name()} = $user;
				}

				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
# The following was added 20100219
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			::rptMsg("Domain Accounts");
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				next unless ($name =~ m/^S\-1/);
				
				(exists $profiles{$name}) ? (::rptMsg($name." [".$profiles{$name}."]")) 
				                          : (::rptMsg($name));
#				::rptMsg("LastWrite time: ".gmtime($s->get_timestamp()));
#				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
		
# Domain Cache?
		eval {
			my @cache = $key->get_subkey("DomainCache")->get_list_of_values();
			if (scalar @cache > 0) {
				::rptMsg("");
				::rptMsg("DomainCache");
				foreach my $d (@cache) {
					my $str = sprintf "%-15s %-20s",$d->get_name(),$d->get_data();
					::rptMsg($str);
				}
			}
		};
		
		
	}
	else {
		::rptMsg($key_path." not found.");
	} 
	
	

}
1;