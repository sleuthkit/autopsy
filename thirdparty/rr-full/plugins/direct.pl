#-----------------------------------------------------------
# direct.pl
# This plugin runs through the Direct* subkeys beneath the Microsoft key
# in the Software hive (as well as the Wow6432Node key, if it exists) and 
# looks to see if there is a MostRecentApplication subkey; if there is, it
# then tries to retrieve the "Name" value/data
#
# Ref:
#  https://twitter.com/SBousseaden/status/1171461656724955143
#
# History:
#  20200904 - MITRE updates
#  20200515 - updated date output format
#  20190911 - added ref
#  20120513 - created
#
# copyright 2019-2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package direct;
use strict;

my %config = (hive          => "software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              MITRE         => "",
			  output		=> "report",
              category      => "program execution",
              version       => 20200904);

sub getConfig{return %config}
sub getShortDescr {
	return "Searches Direct* keys for MostRecentApplication subkeys";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my @keys = ('Microsoft','Wow6432Node\\Microsoft');
	
	::rptMsg("Launching direct v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	foreach my $key_path (@keys) {
	  my $key;
	  if ($key = $root_key->get_subkey($key_path)) {
		  ::rptMsg($key_path);
#		  ::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#		  ::rptMsg("");
		  my @subkeys = $key->get_list_of_subkeys();
		  if (scalar(@subkeys) > 0) {
		    foreach my $s (@subkeys) {
			  	next unless ($s->get_name() =~ m/^Direct/);
			  	my $name = $s->get_name();
			  	
			  	eval {
			  		my $app;
			  		$app = $s->get_subkey("MostRecentApplication");
			  		my $app_lw = ::format8601Date($app->get_timestamp())."Z";
			  		my $app_name = $app->get_value("Name")->get_data();
			  		::rptMsg(sprintf "%-25s  %-50s",$app_lw,$s->get_name()."\\".$app->get_name()." - ".$app_name);
			  		
			  	};
				}
			}
			else {
		  	::rptMsg($key_path." has no subkeys.");
			}
			::rptMsg("");
		}
		else {
	 		::rptMsg($key_path." not found.");
		}
	} 
}
1;