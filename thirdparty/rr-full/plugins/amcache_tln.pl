#-----------------------------------------------------------
# amcache_tln.pl 
#   
# Change history
#   20170315 - created
#
# References
#   http://www.swiftforensics.com/2013/12/amcachehve-in-windows-8-goldmine-for.html
#
# Copyright (c) 2017 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package amcache_tln;
use strict;

my %config = (hive          => "amcache",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              category      => "program execution",
              version       => 20170315);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Parse AmCache\.hve file, TLN format";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	
	# Initialize #
	::logMsg("Launching amcache_tln v.".$VERSION);
#  ::rptMsg("amcache v.".$VERSION); 
#  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");     
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my @sk1;
	my @sk;
	my (@t,$gt);
	
	my $key_path = 'Root\\File';
#	::rptMsg("***Files***");
	if ($key = $root_key->get_subkey($key_path)) {
		
		@sk1 = $key->get_list_of_subkeys();
		foreach my $s1 (@sk1) {
# Volume GUIDs			
			::rptMsg($s1->get_name());
			
			@sk = $s1->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					my $fileref = $s->get_name();
					my $lw      = $s->get_timestamp();

# First, report key lastwrite time (== execution time??)					
					eval {
						$fileref = $fileref.":".$s->get_value("15")->get_data();
					};
					
					::rptMsg($lw."|AmCache|||Key LastWrite   - ".$fileref);
					
# get last mod./creation times									
					my @dots = qw/. . . ./;
					my %t_hash = ();
					my @vals = ();
					
# last mod time
					eval {
						my @t = unpack("VV",$s->get_value("11")->get_data());
						$vals[1] = ::getTime($t[0],$t[1]);
					};
# creation time
					eval {
						my @t = unpack("VV",$s->get_value("12")->get_data());
						$vals[3] = ::getTime($t[0],$t[1]);
					};

					foreach my $v (@vals) {
						@{$t_hash{$v}} = @dots unless ($v == 0);
					}

					${$t_hash{$vals[0]}}[1] = "A" unless ($vals[0] == 0);
					${$t_hash{$vals[1]}}[0] = "M" unless ($vals[1] == 0);
					${$t_hash{$vals[2]}}[2] = "C" unless ($vals[2] == 0);
					${$t_hash{$vals[3]}}[3] = "B" unless ($vals[3] == 0);

					foreach my $t (reverse sort {$a <=> $b} keys %t_hash) {
						my $str = join('',@{$t_hash{$t}});
						::rptMsg($t."|AmCache|||".$str."  ".$fileref);
					}
						
# check for PE Compile times					
					eval {
						my $pe = $s->get_value("f")->get_data();
						::rptMsg($pe."|AmCache|||PE Compile time - ".$fileref);
						::rptMsg("Compile Time  : ".$gt." Z");
					};
					
				}
			}
		}
	}
}

1;