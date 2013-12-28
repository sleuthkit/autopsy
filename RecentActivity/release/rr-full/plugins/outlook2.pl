#------------------------------------------------------------------------------
# outlook2.pl
#   A step in the swampy MAPI
#   Plugin for RegRipper
#  * BETA open to suggestions and corrections *
#
# Change history
#   20130308 created
#
# References
#   [1] http://www.windowsitpro.com/article/registry2/inside-mapi-profiles-45347
#   [2] http://msdn.microsoft.com/en-us/library/ms526356(v=exchg.10).aspx
#
# Todo
#   001f6700 PST
#   001f6610 OST
#
# copyright 2013 Realitynet System Solutions snc
# author: francesco picasso <francesco.picasso@gmail.com>
#------------------------------------------------------------------------------
package outlook2;
use strict;

use Parse::Win32Registry qw( unpack_windows_time
                             unpack_unicode_string
                             unpack_sid
                             unpack_ace
                             unpack_acl
                             unpack_security_descriptor );

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130308);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets MAPI (Outlook) settings *BETA*";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my %outlook_subkeys;

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching outlook2 v.".$VERSION);
    ::rptMsg("outlook2 v.".$VERSION);
    ::rptMsg("(".getHive().") ".getShortDescr()."\n");
	
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

    my $tab;
	my $key;
	my $key_path;
    my $outlook_key_path = 'Software\\Microsoft\\Windows NT\\CurrentVersion\\Windows Messaging Subsystem\\Profiles\\Outlook';
    my $accounts_key_name = '9375CFF0413111d3B88A00104B2A6676';
    ::rptMsg("Working path is '$key_path'");
    ::rptMsg("");
    
    $key = $root_key->get_subkey($outlook_key_path);
    if (!$key) { ::rptMsg("Outlook key not found"); return; }
    my @subkeys = $key->get_list_of_subkeys();
    foreach my $s (@subkeys) { $outlook_subkeys{$s->get_name()} = $s; }

    # Accessing ACCOUNTS
    # "Another well-known GUID is 9375CFF0413111d3B88A00104B2A6676, which is
    # used to hold details about all the accounts that are in use within the 
    # profile. Under this subkey, you will find a subkey per account.
    # For example, you'll typically find a subkey relating to the Outlook
    # Address Book (OAB) account, the Exchange account, an account for each PST
    # file that's been added to the profile, and any POP3/IMAP mail accounts
    # that are defined within the profile." Ref[1]
    $key_path = $outlook_key_path.'\\'.$accounts_key_name;
    $key = $root_key->get_subkey($key_path);
    if (!$key) { ::rptMsg("Accounts key '$accounts_key_name' not found"); return; }
    ::rptMsg("__key_ $accounts_key_name");
    ::rptMsg("_time_ ".gmtime($key->get_timestamp()));
    ::rptMsg("_desc_ accounts used within the profile");
    ::rptMsg("");
    
    my @accounts_keys = $key->get_list_of_subkeys();
    foreach my $account_key (@accounts_keys)
    {
        $tab = '  ';
        ::rptMsg($tab.'-----------------------------------');
        ::rptMsg($tab.$account_key->get_name()." [".gmtime($account_key->get_timestamp())."]");
        ::rptMsg($tab.'-----------------------------------');
        ::rptMsg($tab.get_unicode_string($account_key, 'Account Name'));
        ::rptMsg($tab.get_dword_string_long($account_key, 'MAPI provider'));
        ::rptMsg($tab.get_dword_string($account_key, 'Mini UID'));
        ::rptMsg($tab.get_unicode_string($account_key, 'Service Name'));
        ::rptMsg($tab.get_hex_string($account_key, 'Service UID'));

        my $service_id_key_name = $account_key->get_value('Service UID');
        if (!$service_id_key_name) { ::rptMsg(""); next; }
        
        ::rptMsg($tab.'\\');
        $tab = '   ';
        parse_service($root_key, $outlook_key_path, $service_id_key_name, $tab);
        $tab = '  ';
        ::rptMsg($tab.'/');

        ::rptMsg($tab.get_dword_string($account_key, 'XP Status'));
        ::rptMsg($tab.get_hex_string($account_key, 'XP Provider UID'));
        
        my $xp_id_key_name = $account_key->get_value('XP Provider UID');
        if (!$xp_id_key_name) { ::rptMsg(""); next; }
        ::rptMsg($tab.'\\');
        $tab = '   ';        
        parse_service($root_key, $outlook_key_path, $xp_id_key_name, $tab, 1);
        $tab = '  ';
        ::rptMsg($tab.'/');

        ::rptMsg("");
    }
    $tab = '';
    ::rptMsg("");
    ::rptMsg("Outlook subkeys not direclty linked to accounts");
    foreach my $okey_name (keys %outlook_subkeys)
    {
        ::rptMsg($tab."$okey_name");
    }
}

sub parse_service
{
    my $root_key = shift;
    my $outlook_key_path = shift;
    my $ids = shift;
    my $tab = shift;
    my $xp_type = shift;

    $ids = $ids->get_raw_data();
    my $num_of_ids = length($ids) / 16;
    for (my $i = 0; $i < $num_of_ids; $i += 1)
    {
        my $service_id_key_name = join('', unpack('(H2)16', $ids));
        $ids = substr($ids, 16);
        my $service_id_key = $root_key->get_subkey($outlook_key_path.'\\'.$service_id_key_name);
        if (!$service_id_key)
        {
            ::rptMsg($tab.'Service UID not found in Outlook path!');
            if (($i+1) != $num_of_ids) { ::rptMsg($tab.'+'); }
            next;
        }       
        ::rptMsg($tab.$service_id_key_name.' ['.gmtime($service_id_key->get_timestamp()).']');
        ::rptMsg($tab.'--------------------------------');
        
        delete($outlook_subkeys{$service_id_key_name});

        if ($xp_type)
        {
            ::rptMsg($tab.get_ascii_string($service_id_key, '001e660b', 'User'));
            ::rptMsg($tab.get_ascii_string($service_id_key, '001e6614', 'Server'));
            ::rptMsg($tab.get_ascii_string($service_id_key, '001e660c', 'Server Name'));
            ::rptMsg($tab.get_unicode_string($service_id_key, '001f662b', 'Server Domain(?)'));
            ::rptMsg($tab.get_unicode_string($service_id_key, '001f3001', 'Display Name'));
            ::rptMsg($tab.get_unicode_string($service_id_key, '001f3006', 'Provider Display'));
            ::rptMsg($tab.get_unicode_string($service_id_key, '001f300a', 'Provider DLL Name'));
        }
        else
        {
            ::rptMsg($tab.get_unicode_string($service_id_key, '001f3001', 'Display Name'));
            ::rptMsg($tab.get_unicode_string($service_id_key, '001f3d0a', 'Service DLL Name'));
            ::rptMsg($tab.get_unicode_string($service_id_key, '001f3d0b', 'Service Entry'));
        }

        if (($i+1) != $num_of_ids) { ::rptMsg($tab.'+'); }
    }
}

sub get_hex_string
{
    my $key = shift;
    my $value = shift;
    my $data = $key->get_value($value);
    if ($data) { $data = join('', unpack('(H2)*', $data->get_raw_data()));}
    else { $data = '<no value>'; }
    return sprintf("%-20s %s", $value.':', $data);
}

sub get_dword_string
{
    my $key = shift;
    my $value = shift;
    my $data = $key->get_value($value);
    if ($data) { $data = $data->get_data(); $data = sprintf('0x%08X', $data); }
    else { $data = '<no value>'; }
    return sprintf("%-20s %s", $value.':', $data);
}

sub get_dword_string_long
{
    my $key = shift;
    my $value = shift;
    my $data = $key->get_value($value);
    if ($data) { $data = $data->get_data(); $data = sprintf('%u [0x%08X]', $data, $data); }
    else { $data = '<no value>'; }
    return sprintf("%-20s %s", $value.':', $data);
}

sub get_unicode_string
{
    my $key = shift;
    my $value = shift;
    my $value_desc = shift;
    my $data = $key->get_value($value);
    if ($data) { $data = unpack_unicode_string($data->get_data()); }
    else { $data = '<no value>'; }
    if (!$value_desc) { return sprintf("%-20s %s", $value.':', $data); }
    return sprintf("%s %-20s %s", $value, '['.$value_desc.']:', $data);
}

sub get_ascii_string
{
    my $key = shift;
    my $value = shift;
    my $value_desc = shift;
    my $data = $key->get_value($value);
    if ($data) { $data = $data->get_data(); } else { $data = '<no value>'; }
    if (!$value_desc) { return sprintf("%-20s %s", $value.':', $data); }
    return sprintf("%s %-20s %s", $value, '['.$value_desc.']:', $data);
}

1;