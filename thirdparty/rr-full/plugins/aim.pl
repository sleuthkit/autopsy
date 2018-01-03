#-----------------------------------------------------------
# aim
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package aim;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080325);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets info from the AOL Instant Messenger (not AIM) install";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching aim plugin v.".$VERSION);
	::rptMsg("aim v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Software\\America Online\\AOL Instant Messenger (TM)\\CurrentVersion\\Users';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("AIM");
		::rptMsg($key_path);
		::rptMsg("");
		
		my @subkeys = $key->get_list_of_subkeys();
		
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) { 
				my $user = $s->get_name();
				::rptMsg("User: $user  [".gmtime($s->get_timestamp())."]");
				
				my $login = "Login";
				my $recent  = "recent IM ScreenNames";
				my $recent2 = "recent ScreenNames";
				
				my @userkeys = $s->get_list_of_subkeys();
				foreach my $u (@userkeys) {
					my $us = $u->get_name();
# See if we can get the encrypted password
					if ($us =~ m/^$login/) {
						my $pwd = "";
						eval {
							$pwd = $u->get_value("Password1")->get_data();
						};
						::rptMsg("Pwd: ".$pwd) if ($pwd ne "");
					}
# See if we can get recent folks they've chatted with...					
					if ($us eq $recent || $us eq $recent2) {
						
						my @vals = $u->get_list_of_values();
						if (scalar(@vals) > 0) {
							::rptMsg($user."\\".$us);
							my %sns;
							foreach my $v (@vals) {
								$sns{$v->get_name()} = $v->get_data();
							}
							
							foreach my $i (sort {$a <=> $b} keys %sns) {
								::rptMsg("\t\t".$i." -> ".$sns{$i});
							}
						}
						else {
# No values							
						}
					}
				}
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
			::logMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;