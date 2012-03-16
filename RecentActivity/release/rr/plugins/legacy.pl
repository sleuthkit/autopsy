#-----------------------------------------------------------
# legacy.pl
# 
#
# Change history
#   20090429 - created
# 
# Reference: http://support.microsoft.com/kb/310592
#   
#
# Analysis Tip: 
#
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package legacy;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090429);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists LEGACY_ entries in Enum\\Root key";	
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
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$legacy{$t}}) {
					::rptMsg("\t$item");
				}
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