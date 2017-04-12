#-----------------------------------------------------------
# startpage.pl
# For Windows 7
#
# Change history
#	  20100330 - created
#
# References
#   
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package startpage;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100330);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's StartPage key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching startpage v.".$VERSION);
	 ::rptMsg("startpage v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartPage";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $menu;
		my $balloon;
		
		eval {
			my $val = $key->get_value("StartMenu_Start_Time")->get_data();
			my ($t0,$t1) = unpack("VV",$val);
			$menu = ::getTime($t0,$t1);
			::rptMsg("StartMenu_Start_Time   = ".gmtime($menu)." Z");
		};
		::rptMsg("Error: ".@$) if (@$);
		
		eval {
			my $val = $key->get_value("StartMenu_Balloon_Time")->get_data();
			my ($t0,$t1) = unpack("VV",$val);
			$balloon = ::getTime($t0,$t1);
			::rptMsg("StartMenu_Balloon_Time = ".gmtime($balloon)." Z");
		};
		::rptMsg("Error: ".@$) if (@$);
		
		
		
		
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;