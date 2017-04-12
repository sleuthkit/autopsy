#-----------------------------------------------------------
# installedcomp.pl
# Get info about Installed Components
#
# Change history:
#   20130911 - updated to check for other than .DLL in StubPath entries
#   20130905 - updated output format to be more searchable, added key names
#   20130410 - added Wow6432Node support
#   20100116 - updated for slightly better coverage
#   20100115 - created
#
# References:
#   http://www.microsoft.com/security/portal/threat/encyclopedia/entry.aspx?Name=Backdoor%3AWin32%2FBifrose.ACI#tab=2
#
#   
# Notes: Look for unusual entries, particularly those that point to the Recycle 
#        Bin, a temp folder, or to a .cpl file
#        
# copyright 2013 Quantum Analytics Research, LLC
# Author: H. Carvey, 2013
#-----------------------------------------------------------
package installedcomp;
use strict;

my %config = (hive          => "Software",
							category      => "malware",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20130911);

sub getConfig{return %config}

sub getShortDescr {
	return "Get info about Installed Components and StubPath values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %comp;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching installedcomp v.".$VERSION);
	::rptMsg("installedcomp v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Active Setup\\Installed Components",
	             "Wow6432Node\\Microsoft\\Active Setup\\Installed Components");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
		
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
					my $lastwrite = $s->get_timestamp();
							
					my $str = $s->get_name().",";
					eval {
						my $id = $s->get_value("ComponentID")->get_data();
						$str .= $id.",";
					};
				
					eval {
						my $ver = $s->get_value("Version")->get_data();
						$str .= " v.".$ver if ($ver && $s->get_value("Version")->get_type() == 1);
					};
				
					eval {
						my $stub = $s->get_value("StubPath")->get_data();
						$str .= ", ".$stub if ($stub ne "");
# added 20130911 - malware can use the Installed Components 			
						my $lcstub = $stub;
						$lcstub =~ tr/[A-Z]/[a-z]/;
						if (grep(/rundll32/,$lcstub)) {
							my $l = (split(/,/,$lcstub,2))[0];
							my $r = (split(/\s/,$l,2))[1];
							$r =~ s/\"//g;
							if ($r =~ m/\.dll$/) {
#								::rptMsg($key_path."\\".$s->get_name()." StubPath value ends in \.dll");
							}
							else {
								::alertMsg($key_path."\\".$s->get_name()." StubPath value DOES NOT point to \.dll: ".$stub);
							}
						}
					};

# If the $str scalar is empty at this point, that means that for
# some reason, we haven't been able to populate the information
# we're looking for; in this case, we'll go looking for some info
# in a different area of the hive; the BHO.pl plugin does this, as
# well.  I'd rather that the plugin look for the Classes info than
# leave a blank entry in the output.
					if ($str eq "") {
						my $name = $s->get_name();
						my $class_path = "Classes\\CLSID\\".$name;
						my $proc;
						if ($proc = $root_key->get_subkey($class_path)) {
# Try these two eval{} statements because I've seen the different 
# spellings for InProcServer32/InprocServer32 in sequential keys
							eval {
								$str = $proc->get_subkey("InprocServer32")->get_value("")->get_data();
							};
						
							eval {
								$str = $proc->get_subkey("InProcServer32")->get_value("")->get_data();
							};
						}
						else {
							$str = $name." class not found.";
						}
					}
					push(@{$comp{$lastwrite}},$str);
				}

				foreach my $t (reverse sort {$a <=> $b} keys %comp) {
#					::rptMsg(gmtime($t)." (UTC)");
					foreach my $item (@{$comp{$t}}) {
						::rptMsg(gmtime($t)." (UTC),".$item);
					}
				}
			}
			else {
				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
}
1;