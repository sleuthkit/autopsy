#-----------------------------------------------------------
# init_dlls.pl
# Plugin to assist in the detection of malware per Mark Russinovich's
#   blog post (References, below)
#
# Change History:
#   20110309 - created
#
# References
#   http://blogs.technet.com/b/markrussinovich/archive/2011/02/27/3390475.aspx
#
# copyright 2011 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package init_dlls;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20110309);

sub getConfig{return %config}

sub getShortDescr {
	return "Check for odd **pInit_Dlls keys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my @init;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching init_dlls v.".$VERSION);
	::rptMsg("init_dlls v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Windows";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("init_dlls");
		::rptMsg($key_path);
		::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				next if ($name eq "AppInit_DLLs");
				push(@init,$name) if ($name =~ m/Init_DLLs$/);
			}
			
			if (scalar @init > 0) {
				foreach my $n (@init) {
					::rptMsg($n);
				}
			}
			else {
				::rptMsg("No additional values named *Init_DLLs located.");
			}
			
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;