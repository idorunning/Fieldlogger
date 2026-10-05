export function weatherKind(code:number,wind:number){
  if([71,73,75,77,85,86].includes(code))return 'snow';
  if(code>=95)return 'storm';
  if([51,53,55,56,57,61,63,65,66,67,80,81,82].includes(code))return 'rain';
  if(wind>=28)return 'wind';
  if(code===45||code===48)return 'fog';
  return code<=1?'sunny':'cloudy';
}
