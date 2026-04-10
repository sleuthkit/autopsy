#-----------------------------------------------------------
# winver.pl
#
#
# Change History:
#   20200916 - MITRE updates
#   20200525 - updated date output format, other updates
#   20081210 - created
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winver;
use strict;

my %config = (hive          => "Software",
              MITRE         => "",
              category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200916);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Windows version & build info";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching winver v.".$VERSION);
	::rptMsg("winver v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
  
  
  my %vals = (1 => "ProductName",
              2 => "ReleaseID",
              3 => "CSDVersion",
              4 => "BuildLab",
              5 => "BuildLabEx",
              6 => "CompositionEditionID",
              7 => "RegisteredOrganization",
              8 => "RegisteredOwner");
         
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Microsoft\\Windows NT\\CurrentVersion";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		foreach my $v (sort {$a <=> $b} keys %vals) {
			
			eval {
				my $i = $key->get_value($vals{$v})->get_data();
				::rptMsg(sprintf "%-25s %-20s",$vals{$v},$i);
			};
		}
		
		eval {
			my $install = $key->get_value("InstallDate")->get_data();
			::rptMsg(sprintf "%-25s %-20s","InstallDate",::format8601Date($install)."Z");
		};
	
		eval {
			my $it = $key->get_value("InstallTime")->get_data();
			my ($t0,$t1) = unpack("VV",$it);
			my $t = ::getTime($t0,$t1);
			::rptMsg(sprintf "%-25s %-20s","InstallTime",::format8601Date($t)."Z");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;