#-----------------------------------------------------------
# legacy_tln.pl
# 
#
# Change history
#   20120620 - modified legacy.pl to legacy_tln.pl
#   20090429 - legacy.pl created
# 
# Reference: http://support.microsoft.com/kb/310592
#   
#
# Analysis Tip: 
# The keys of interested begin with LEGACY_<servicename>, for example, 
# "LEGACY_EVENTSYSTEM".  The LastWrite time on this key seems to indicate
# the first time that the serivce was launched.  The LastWrite time on 
# keys named, for example, "LEGACY_EVENTSYSTEM\0000", appear to indicate
# the most recent time that the service was launched. One example to look
# for is services related to malware/lateral movement, such as PSExec.
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package legacy_tln;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120620);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists LEGACY_* entries in Enum\\Root key in TLN format";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key();
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		my $root_path = $ccs."\\Enum\\Root";
		
		my %legacy;
		if (my $root = $root_key->get_subkey($root_path)) {
			my @sk = $root->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					my $name = $s->get_name();
					next unless ($name =~ m/^LEGACY_/);
					push(@{$legacy{$s->get_timestamp()}},$name);
				
					eval {
						my @s_sk = $s->get_list_of_subkeys();
						if (scalar(@s_sk) > 0) {
							foreach my $s_s (@s_sk) {
								
								my $desc;
								eval {
									$desc = $s_s->get_value("DeviceDesc")->get_data();
									push(@{$legacy{$s_s->get_timestamp()}},$name."\\".$s_s->get_name()." - ".$desc);
								};
								push(@{$legacy{$s_s->get_timestamp()}},$name."\\".$s_s->get_name()) if ($@);
							}
						}
					};
				}
			}
			else {
				::rptMsg($root_path." has no subkeys.");
			}
			
			foreach my $t (reverse sort {$a <=> $b} keys %legacy) {
				foreach my $item (@{$legacy{$t}}) {
					::rptMsg($t."|REG|||[Program Execution] - $item");
				}
								
#				::rptMsg(gmtime($t)." (UTC)");
#				foreach my $item (@{$legacy{$t}}) {
#					::rptMsg("  ".$item);
#				}
			}
		}
		else {
			::rptMsg($root_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;