#-----------------------------------------------------------
# outlook.pl
#   **Very Beta!  Based on one sample hive file only!
#
# Change history
#   20100218 - created
#
# References
#    
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package outlook;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100218);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's Outlook settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	my %hist;
	::logMsg("Launching outlook v.".$VERSION);
	::rptMsg("outlook v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows Messaging Subsystem\\Profiles';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			::rptMsg("");
			foreach my $s (@subkeys) {
				
				my $profile = $s->get_name();
				::rptMsg($profile." Profile");

# AutoArchive settings
# http://support.microsoft.com/kb/198479				
				eval {
					my $data = $s->get_subkey("0a0d020000000000c000000000000046")->get_value("001f0324")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Outlook 2007 AutoArchive path -> ".$data);
				};
				
				eval {
					my $data = $s->get_subkey("0a0d020000000000c000000000000046")->get_value("001e0324")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Outlook 2003 AutoArchive path -> ".$data);
				};
				
				eval {
					my $data = $s->get_subkey("0a0d020000000000c000000000000046")->get_value("001e032c")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Outlook 2003 AutoArchive path (alt) -> ".$data);
				};
				
# http://support.microsoft.com/kb/288570				
				eval {
					my $data = $s->get_subkey("0a0d020000000000c000000000000046")->get_value("101e0384")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Open Other Users MRU (Outlook 97) -> ".$data);
				};
				
				eval {
					my $data = $s->get_subkey("0a0d020000000000c000000000000046")->get_value("101f0390")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Open Other Users MRU (Outlook 2003) -> ".$data);
				};
				
				
				
				eval {
					my $data = unpack("V",$s->get_subkey("13dbb0c8aa05101a9bb000aa002fc45a")->get_value("00036601")->get_data());
					my $str;
					if ($data == 4) {
						$str = "  Cached Exchange Mode disabled.";
					}
					elsif ($data == 4484) {
						$str = "  Cached Exchange Mode enabled.";
					}
					else {
						$str = sprintf "  Cached Exchange Mode: 0x%x",$data;
					}
					::rptMsg($str);
				};
				
				eval {
					my $data = $s->get_subkey("13dbb0c8aa05101a9bb000aa002fc45a")->get_value("001f6610")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Path to OST file: ".$data);
				};
				
				eval {
					my $data = $s->get_subkey("13dbb0c8aa05101a9bb000aa002fc45a")->get_value("001f6607")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Email: ".$data);
				};
				
				eval {
					my $data = $s->get_subkey("13dbb0c8aa05101a9bb000aa002fc45a")->get_value("001f6620")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Email: ".$data);
				};
				
# http://support.microsoft.com/kb/959956				
#				eval {
#					my $data = $s->get_subkey("13dbb0c8aa05101a9bb000aa002fc45a")->get_value("01026687")->get_data();
#					$data =~ s/\x00/\./g;
#					$data =~ s/\W//g;
#					::rptMsg("  Non-SMTP Email: ".$data);
#				};
				
				
					
					
					
					
					
				
				
				
				
				
				
				
				eval {
					my $data = $s->get_subkey("0a0d020000000000c000000000000046")->get_value("001e032c")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  Outlook 2003 AutoArchive path (alt) -> ".$data);
				};
		
		
			
				
				
				
				eval {
					my $data = $s->get_subkey("0a0d020000000000c000000000000046")->get_value("001f0418")->get_data();
					$data =~ s/\x00//g;
					::rptMsg("  001f0418 -> ".$data);
				};
#				::rptMsg("Error : ".$@) if ($@);
				
				
# Account Names and signatures
# http://support.microsoft.com/kb/938360			
				my @subkeys = $s->get_subkey("9375CFF0413111d3B88A00104B2A6676")->get_list_of_subkeys();
				if (scalar @subkeys > 0) {
					
					foreach my $s2 (@subkeys) {
						eval {
							
							
						};
					}
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

1;