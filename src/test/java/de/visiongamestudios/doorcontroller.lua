local friends={'empire','jmonkeyrulez'}

local duser
local ddoor
function canOpenDoor(user,door)
  --reduce cost of canOpenDoor as it might have a very low limit, and do work in the higher limid tick call
  door.processing()
  duser = user
  ddoor = door
  
end

function tick()
  if ddoor and duser then
  print("Testing user " .. duser.name);
    if table.contains(friends, duser.name) then
      ddoor.open()
    else
      ddoor.deny()
    end
    ddoor = nil
    duser = nil
  end
end