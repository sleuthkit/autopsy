#-----------------------------------------------------------
# profilelist.pl
# Gets ProfileList subkeys and ProfileImagePath value; also
# gets the ProfileLoadTimeHigh and Low values, and translates them
# into a readable time
#
# History:
#   20100219 - updated to gather SpecialAccounts and domain
#              user info
#   20080415 - created
#
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package autopsyprofilelist;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100219);

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
	
#	::logMsg("Launching profilelist v.".$VERSION);
#	::rptMsg("profilelist v.".$VERSION); # banner
#    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

		::rptMsg("<ProfileList>");
		::rptMsg("<mtime></mtime>");
		::rptMsg("<artifacts>");

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\ProfileList";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
	#	::rptMsg($key_path);
	#	::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
	#	::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				my $path;
				eval {
					$path = $s->get_value("ProfileImagePath")->get_data();
				};
				
				#::rptMsg("Path      : ".$path);
				#::rptMsg("SID       : ".$s->get_name());
				#::rptMsg("LastWrite : ".gmtime($s->get_timestamp())." (UTC)");
				
				my $sid = $s->get_name();

				my $user;
				if ($path) {
					my @a = split(/\\/,$path);
					my $end = scalar @a - 1;
					$user = $a[$end];

					# Strip off the domain after the dot (if present)
					$user =~ s/\..*//;

				}

				::rptMsg("<user sid=\"" . $sid  . "\" username=\"" . $user . "\">" 
					 . $path . "</user>");

				
				#my @load;
				#eval {
				#	$load[0] = $s->get_value("ProfileLoadTimeLow")->get_data();
				#	$load[1] = $s->get_value("ProfileLoadTimeHigh")->get_data();
				#};
				#if (@load) {
				#	my $loadtime = ::getTime($load[0],$load[1]);
				#	::rptMsg("LoadTime  : ".gmtime($loadtime)." (UTC)");
				#}
				#::rptMsg("");
			}
		}
		else {
			#::rptMsg($key_path." has no subkeys.");
			#::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		#::rptMsg($key_path." not found.");
		#::logMsg($key_path." not found.");
	}
	
# The following was added 20100219
#	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
#	if ($key = $root_key->get_subkey($key_path)) {
#		my @subkeys = $key->get_list_of_subkeys();
#		if (scalar @subkeys > 0) {
#			::rptMsg("Domain Accounts");
#			foreach my $s (@subkeys) {
#				my $name = $s->get_name();
#				next unless ($name =~ m/^S\-1/);
				
#				(exists $profiles{$name}) ? (::rptMsg($name." [".$profiles{$name}."]")) 
#				                          : (::rptMsg($name));
#				::rptMsg("LastWrite time: ".gmtime($s->get_timestamp()));
#				::rptMsg("");
#			}
#		}
#		else {
#			::rptMsg($key_path." has no subkeys.");
#		}
		
# Domain Cache?
#		eval {
#			my @cache = $key->get_subkey("DomainCache")->get_list_of_values();
#			if (scalar @cache > 0) {
#				::rptMsg("");
#				::rptMsg("DomainCache");
#				foreach my $d (@cache) {
#					my $str = sprintf "%-15s %-20s",$d->get_name(),$d->get_data();
#					::rptMsg($str);
#				}
#			}
#		};
		
		
#	}
#	else {
#		::rptMsg($key_path." not found.");
#	} 
	
	::rptMsg("</artifacts></ProfileList>");	

}
1;
