#-----------------------------------------------------------
# logmein_tln.pl 
# 
#
#
#  
#
# Change history
#   20161011 - created
#
# Copyright 2016 QAR, LLC
#-----------------------------------------------------------
package logmein_tln;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20161011);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of login times via LogMeIn";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching logmein_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# updated added 20130326  
  my @paths = ("LogMeIn\\V5\\PerBrowser",
               "Wow6432Node\\LogMeIn\\V5\\PerBrowser");
    
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
#			::rptMsg($key_path);
#			::rptMsg("");
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
#				  ::rptMsg($s->get_name());
#					::rptMsg("  LastWrite: ".gmtime($s->get_timestamp())." Z");
					
					my @ts = ();
					my $t = "";
					my $u = "";
					
					eval {
						$u = $s->get_value("LASTUSERNAME")->get_data();
					};
					
					eval {
						@ts = unpack("VV",$s->get_value("LastUsed")->get_data());
						$t = ::getTime($ts[0],$ts[1]);
					};
					::rptMsg($t."|REG|||".$u." logged in via LogMeIn");
				}
			}
			else {
#				::rptMsg($key_path." does not appear to have any subkeys.")
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
}
1;