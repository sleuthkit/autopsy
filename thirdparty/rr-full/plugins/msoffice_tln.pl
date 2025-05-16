#-----------------------------------------------------------
# msoffice_tln.pl
# List Office documents for which the user explicitly opted to accept bypassing
#   the default security settings for the application 
#
# Change history
#  20200730 - MITRE ATT&CK Updates
#  20200518 - minor updates
#  20190823 - updated to include AuthHistory LastLoginTime value
#  20190822 - created 
#
# References
# 20190626 updates
#  https://decentsecurity.com/block-office-macros
#  https://gist.github.com/PSJoshi/749cf1733217d8791cf956574a3583a2
#
#  http://az4n6.blogspot.com/2016/02/more-on-trust-records-macros-and.html
#  ForensicArtifacts.com posting by Andrew Case:
#    http://forensicartifacts.com/2012/07/ntuser-trust-records/
#  http://archive.hack.lu/2010/Filiol-Office-Documents-New-Weapons-of-Cyberwarfare-slides.pdf
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package  msoffice_tln;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "user activity",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "tln",
              version       => 20200730);

sub getConfig{return %config}
sub getShortDescr {
	return "Get user's MSOffice content";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $office_version;
my %vba = (1 => "Enable all macros",
           2 => "Disable all macros w/ notification",
           3 => "Disalbe all macros except dig. signed macros",
           4 => "Disalbe all macros w/o notification");
           
sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
#	::logMsg("Launching  msoffice_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
#	::rptMsg("msoffice v.".$VERSION);
#	::rptMsg("");
# First, let's find out which version of Office is installed
	my @version;
	my $key;
	my $key_path = "Software\\Microsoft\\Office";
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		foreach my $s (@subkeys) {
			my $name = $s->get_name();
			push(@version,$name) if ($name =~ m/^\d/);
		}
	}
# Determine MSOffice version in use	
	my @v = reverse sort {$a<=>$b} @version;
	foreach my $i (@v) {
		eval {
			if (my $o = $key->get_subkey($i."\\User Settings")) {
				$office_version = $i;
			}
		};
	}
	
# See if AuthHistory key includes a LastLoginTime value
	eval {
		if (my $id = $key->get_subkey($office_version."\\Common\\Identity\\Identities")) {
			my @subkeys = $id->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				foreach my $s (@subkeys) {
					my $name = $s->get_value("SigninName")->get_data();
					my ($t0,$t1) = unpack("VV",$s->get_subkey("AuthHistory")->get_value("LastLoginTime")->get_data());
					my $l = ::getTime($t0,$t1);
					::rptMsg($l."|REG|||".$name." MSOffice LastLoginTime");
				}
			}
		}
	};	
	
# Now that we have the most recent version of Office installed, let's 
# start looking at the various subkeys
	my @apps = ("Word","PowerPoint","Excel","Access");	
	
	foreach my $app (@apps) {
# TrustRecords and Trusted Locations
# TrustRecords may provide insight into User Execution:Malicious File
# https://attack.mitre.org/techniques/T1204/002/
		eval {
			if (my $trs = $key->get_subkey($office_version."\\".$app."\\Security\\Trusted Documents\\TrustRecords")) {
				my @vals = $trs->get_list_of_values();
				if (scalar @vals > 0) {
#					::rptMsg($app." - TrustRecords");
					foreach my $v (@vals) {
						my $name = $v->get_name();
						my $data = $v->get_data();
						my ($t0,$t1) = (unpack("VV",substr($data,0,8)));
						my $t = ::getTime($t0,$t1);
#						my $out_str = gmtime($t)." UTC: ".$v->get_name();
						my $out_str = $t."|REG|||".$app." TrustRecords - ".$v->get_name();
						my $e = unpack("V",substr($data, length($data) - 4, 4));
						$out_str .= " **[T1204\.002] Enable Content button clicked." if ($e == 2147483647);
						::rptMsg($out_str);
					}
				}
			}
#			::rptMsg("");
	  };
	  

# File MRUs		
		eval {
			if (my $fm = $key->get_subkey($office_version."\\".$app."\\File MRU")) {
				my @vals = $fm->get_list_of_values();
				if (scalar @vals > 0) {
#					::rptMsg($app." - File MRU");
					foreach my $v (@vals) {
						next unless ($v->get_name() =~ m/^Item/);
						my ($t,$file) = processMRUValue($v->get_data());
#						::rptMsg(gmtime($t)." UTC: ".$file);
						::rptMsg($t."|REG|||".$app." File MRU - ".$file);
					}
				}
			}
		};
		
		eval {
			if (my $um = $key->get_subkey($office_version."\\".$app."\\User MRU")) {
				my @subkeys = $um->get_list_of_subkeys();
				if (scalar @subkeys > 0) {
					foreach my $s (@subkeys) {
						my @vals = $s->get_subkey("File MRU")->get_list_of_values();
						if (scalar @vals > 0) {
#							::rptMsg($app." - File MRU");
							foreach my $v (@vals) {
								next unless ($v->get_name() =~ m/^Item/);
								my ($t,$file) = processMRUValue($v->get_data());
#						    ::rptMsg(gmtime($t)." UTC: ".$file);
								::rptMsg($t."|REG|||".$app."\\User MRU\\".$s->get_name()." - File MRU - ".$file);
							}
						}
					}
				}
			}
		};
		
# Place MRU		
		eval {
			if (my $fm = $key->get_subkey($office_version."\\".$app."\\Place MRU")) {
				my @vals = $fm->get_list_of_values();
				if (scalar @vals > 0) {
#					::rptMsg($app." - Place MRU");
					foreach my $v (@vals) {
						my $name = $v->get_name();
						next unless ($v->get_name() =~ m/^Item/);
						my ($t,$file) = processMRUValue($v->get_data());
#						::rptMsg(gmtime($t)." UTC: ".$file);
						::rptMsg($t."|REG|||".$app." Place MRU - ".$file);
					}
				}
			}
		};
		
		eval {
			if (my $um = $key->get_subkey($office_version."\\".$app."\\User MRU")) {
				my @subkeys = $um->get_list_of_subkeys();
				if (scalar @subkeys > 0) {
					foreach my $s (@subkeys) {
						my @vals = $s->get_subkey("Place MRU")->get_list_of_values();
						if (scalar @vals > 0) {
#							::rptMsg($app." - Place MRU");
							foreach my $v (@vals) {
								next unless ($v->get_name() =~ m/^Item/);
								my ($t,$file) = processMRUValue($v->get_data());
#						    ::rptMsg(gmtime($t)." UTC: ".$file);
								::rptMsg($t."|REG|||".$app."\\User MRU\\".$s->get_name()." - Place MRU - ".$file);
							}
						}
					}
				}
			}
		};
	}
	
# Word Reading Locations
	eval {
		if (my $rl = $key->get_subkey($office_version."\\Word\\Reading Locations")) {
			my @subkeys = $rl->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
#				::rptMsg("Word - Reading Locations");
				foreach my $s (@subkeys) {
					my $path = $s->get_value("File Path")->get_data();
					my $dt   = $s->get_value("Datetime")->get_data();
#					::rptMsg(gmtime($s->get_timestamp())." UTC");
#					::rptMsg($path." (".$dt.")");
					::rptMsg($s->get_timestamp()."|REG|||MSWord Reading Locations - $path (".$dt.")");
				}
			}
		}
	};	
}


sub processMRUValue {
	my $str = shift;
	my ($stuff,$file) = split(/\*/,$str);
	my $t_str = (split(/\]\[/,$stuff))[1];
	$t_str =~ s/^T//;
	my $t = ::getFileTimeStr($t_str);
	return ($t,$file);
}


1;