#-----------------------------------------------------------
# systemindex.pl
# 
# Note: Andrew Case pointed out this key to me on 16 July 2012,
# and after seeing what was in it, I just wrote up a plugin
#
# History:
#  20201005 - MITRE update
#  20200518 - updated date output format
#  20120716 - created
#
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package systemindex;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              MITRE         => "",
              category      => "user activity",
			  output		=> "report",
              version       => 20201005);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets systemindex\\..\\Paths info from Windows Search key";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching systemindex v.".$VERSION);
	::rptMsg("Launching systemindex v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

  my $key;
  my $key_path = "Microsoft\\Windows Search\\Gather\\Windows\\SystemIndex\\Sites\\LocalHost\\Paths";
  if ($key = $root_key->get_subkey($key_path)) {
	  ::rptMsg($key_path);
	  my @subkeys = $key->get_list_of_subkeys();
	  if (scalar(@subkeys) > 0) {
	    foreach my $s (@subkeys) {
		  	my $name = $s->get_name();
		  	my $ts = $s->get_timestamp();
		  	::rptMsg($name." - LastWrite time: ".::format8601Date($ts)."Z");
		  	
		  	my $path;
		  	eval {
		  		$path = $s->get_value("Path")->get_data();
		  		::rptMsg("Path: ".$path);
		  	};
		  	
		  	
		  	::rptMsg("");
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